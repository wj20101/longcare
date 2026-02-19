package com.ytone.longcare.features.facecapture

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.core.common.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject
import com.ytone.longcare.common.utils.logD

@HiltViewModel
class FaceCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

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
            val savedPath = saveFaceImageToFiles(faceBitmap)
            if (savedPath != null) {
                logD("Face image saved to: $savedPath", tag = "FaceCaptureViewModel")
            }

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

    private suspend fun saveFaceImageToFiles(bitmap: Bitmap): String? {
        return FaceCaptureStorageDelegate.saveFaceImageToFiles(
            context = context,
            bitmap = bitmap,
            ioDispatcher = ioDispatcher
        )
    }

    fun getSavedFaceImages(): List<String> {
        return FaceCaptureStorageDelegate.getSavedFaceImages(context)
    }

    override fun onCleared() {
        super.onCleared()
        FaceCaptureBitmapCacheDelegate.recycleAndClear(bitmapCache)
    }
}
