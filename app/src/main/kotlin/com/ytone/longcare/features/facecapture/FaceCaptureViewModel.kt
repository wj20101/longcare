package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

@HiltViewModel
class FaceCaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(FaceCaptureUiState())
    val uiState: StateFlow<FaceCaptureUiState> = _uiState.asStateFlow()

    private var lastCaptureTime = 0L
    private val captureInterval = 1500L
    private val bitmapCache = mutableListOf<WeakReference<Bitmap>>()

    fun onFaceCaptured(faceBitmap: Bitmap, quality: Float) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val currentState = _uiState.value

            if (!FaceCaptureStateDelegate.canCaptureNewFace(
                    state = currentState,
                    currentTime = currentTime,
                    lastCaptureTime = lastCaptureTime,
                    captureInterval = captureInterval
                )
            ) {
                return@launch
            }

            FaceCaptureBitmapCacheDelegate.cleanup(bitmapCache)
            FaceCaptureBitmapCacheDelegate.add(bitmapCache, faceBitmap)
            _uiState.value = FaceCaptureStateDelegate.withCapturedFace(
                state = currentState,
                faceBitmap = faceBitmap,
                quality = quality
            )
            lastCaptureTime = currentTime
        }
    }

    fun updateProcessingState(isProcessing: Boolean) {
        _uiState.value = FaceCaptureStateDelegate.updateProcessing(_uiState.value, isProcessing)
    }

    fun updateUserHint(hint: String) {
        _uiState.value = FaceCaptureStateDelegate.updateUserHint(_uiState.value, hint)
    }

    fun updateFaceDetectionState(detected: Boolean, quality: Float = 0f) {
        _uiState.value = FaceCaptureStateDelegate.updateFaceDetection(_uiState.value, detected, quality)
    }

    fun selectFace(index: Int) {
        FaceCaptureStateDelegate.selectFace(_uiState.value, index)?.let { _uiState.value = it }
    }

    fun cancelSelection() {
        _uiState.value = FaceCaptureStateDelegate.cancelSelection(_uiState.value)
    }

    fun removeFace(index: Int) {
        FaceCaptureStateDelegate.removeFace(_uiState.value, index)?.let { updatedState ->
            FaceCaptureBitmapCacheDelegate.recycleAndRemoveAt(bitmapCache, index)
            _uiState.value = updatedState
        }
    }

    fun clearAllFaces() {
        FaceCaptureBitmapCacheDelegate.recycleAndClear(bitmapCache)
        _uiState.value = FaceCaptureUiState()
    }

    fun setError(error: String) {
        _uiState.value = FaceCaptureStateDelegate.setError(_uiState.value, error)
    }

    fun clearError() {
        _uiState.value = FaceCaptureStateDelegate.clearError(_uiState.value)
    }

    override fun onCleared() {
        FaceCaptureBitmapCacheDelegate.recycleAndClear(bitmapCache)
    }
}
