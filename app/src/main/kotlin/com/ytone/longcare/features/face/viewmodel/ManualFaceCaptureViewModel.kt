package com.ytone.longcare.features.face.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.features.face.ui.DetectedFace
import com.ytone.longcare.features.face.ui.ManualFaceCaptureState
import com.ytone.longcare.features.face.ui.ManualFaceCaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManualFaceCaptureViewModel @Inject constructor(
    private val facePipelineDelegate: ManualFaceCaptureFacePipelineDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualFaceCaptureUiState())
    val uiState: StateFlow<ManualFaceCaptureUiState> = _uiState.asStateFlow()

    private val _currentState = MutableStateFlow<ManualFaceCaptureState>(ManualFaceCaptureState.Idle)
    val currentState: StateFlow<ManualFaceCaptureState> = _currentState.asStateFlow()

    fun setCameraPermissionGranted(granted: Boolean) {
        applyTransition(ManualFaceCaptureStateTransitions.onCameraPermissionChanged(_uiState.value, granted))
    }

    fun startCapture() {
        applyTransition(ManualFaceCaptureStateTransitions.onStartCapture(_uiState.value))
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        applyTransition(ManualFaceCaptureStateTransitions.onPhotoCaptured(_uiState.value, bitmap))
        detectFaces(bitmap)
    }

    private fun detectFaces(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                applyTransition(ManualFaceCaptureStateTransitions.onDetectionStarted(_uiState.value))
                val detectedFaces = facePipelineDelegate.detectFaces(bitmap)

                applyTransition(ManualFaceCaptureStateTransitions.onFacesDetected(_uiState.value, detectedFaces))
                when {
                    detectedFaces.isEmpty() -> {
                        applyTransition(ManualFaceCaptureStateTransitions.onNoFacesDetected(_uiState.value))
                    }
                    detectedFaces.size == 1 -> {
                        selectFace(0)
                    }
                    else -> {
                        applyTransition(ManualFaceCaptureStateTransitions.onMultipleFacesDetected(_uiState.value))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                applyTransition(ManualFaceCaptureStateTransitions.onDetectionError(_uiState.value, e.message ?: "未知错误"))
            }
        }
    }

    fun selectFace(index: Int) {
        val faces = _uiState.value.detectedFaces
        if (index in faces.indices) {
            applyTransition(ManualFaceCaptureStateTransitions.onFaceSelected(_uiState.value, index))
        }
    }

    private fun showConfirmationDialog() {
        applyTransition(ManualFaceCaptureStateTransitions.showConfirmationDialog(_uiState.value))
    }

    fun hideConfirmationDialog() {
        applyTransition(ManualFaceCaptureStateTransitions.hideConfirmationDialog(_uiState.value))
    }

    fun confirmSelectedFace() {
        val selectedIndex = _uiState.value.selectedFaceIndex
        val faces = _uiState.value.detectedFaces

        if (selectedIndex != null && selectedIndex in faces.indices) {
            val selectedFace = faces[selectedIndex]
            hideConfirmationDialog()
            saveFaceImage(selectedFace)
        }
    }

    fun cancelAndRetake() {
        hideConfirmationDialog()
        resetState()
    }

    private fun saveFaceImage(face: DetectedFace) {
        viewModelScope.launch {
            try {
                applyTransition(ManualFaceCaptureStateTransitions.onSaveStarted(_uiState.value))
                val savedPath = facePipelineDelegate.saveFaceImage(face)
                applyTransition(ManualFaceCaptureStateTransitions.onSaveSuccess(_uiState.value, savedPath))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                applyTransition(ManualFaceCaptureStateTransitions.onSaveError(_uiState.value, e.message ?: "保存失败"))
            }
        }
    }

    fun resetState() {
        applyTransition(ManualFaceCaptureStateTransitions.onReset(_uiState.value))
    }

    fun clearError() {
        applyTransition(ManualFaceCaptureStateTransitions.onClearError(_uiState.value))
    }

    fun getFaceQualityHints(faceIndex: Int): List<String> {
        val faces = _uiState.value.detectedFaces
        val capturedPhoto = _uiState.value.capturedPhoto

        return if (faceIndex in faces.indices && capturedPhoto != null) {
            val face = faces[faceIndex]
            facePipelineDelegate.getFaceQualityHints(face, capturedPhoto)
        } else {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        facePipelineDelegate.release()
    }

    private fun applyTransition(transition: ManualFaceCaptureTransition) {
        _uiState.value = transition.uiState
        transition.state?.let { _currentState.value = it }
    }
}
