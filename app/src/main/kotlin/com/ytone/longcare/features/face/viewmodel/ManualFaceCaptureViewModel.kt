package com.ytone.longcare.features.face.viewmodel

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.R
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.text.ResourceTextResolver
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
    private val facePipelineDelegate: ManualFaceCaptureFacePipelineDelegate,
    private val textResolver: ResourceTextResolver,
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

    internal fun onPhotoCaptureFailed(
        stage: ManualFaceCaptureFailureStage,
        failure: ManualFaceCaptureFailure,
        error: Throwable,
    ) {
        DiagnosticEventTracker.trackError(
            category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
            event = "manual_face_photo_capture_failed",
            description = "手动人脸采集拍照或图片处理失败",
            throwable = error,
            extras = mapOf(
                "stage" to stage.diagnosticCode,
                "failure" to failure.name,
            ),
        )
        applyTransition(
            ManualFaceCaptureStateTransitions.onPhotoCaptureError(
                _uiState.value,
                textResolver.text(failure.messageRes),
            ),
        )
    }

    private fun detectFaces(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                applyTransition(ManualFaceCaptureStateTransitions.onDetectionStarted(_uiState.value))
                val detectedFaces = facePipelineDelegate.detectFaces(bitmap)

                applyTransition(ManualFaceCaptureStateTransitions.onFacesDetected(_uiState.value, detectedFaces))
                when {
                    detectedFaces.isEmpty() -> {
                        applyTransition(
                            ManualFaceCaptureStateTransitions.onNoFacesDetected(
                                _uiState.value,
                                textResolver.text(R.string.manual_face_not_detected),
                            ),
                        )
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
                DiagnosticEventTracker.trackError(
                    category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                    event = "manual_face_detect_exception",
                    description = "手动人脸采集静态图检测异常",
                    throwable = e,
                    extras = mapOf(
                        "bitmapWidth" to bitmap.width,
                        "bitmapHeight" to bitmap.height,
                    ),
                )
                applyTransition(
                    ManualFaceCaptureStateTransitions.onDetectionError(
                        _uiState.value,
                        textResolver.text(R.string.manual_face_detection_failed),
                    ),
                )
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
                DiagnosticEventTracker.trackError(
                    category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                    event = "manual_face_save_exception",
                    description = "手动人脸采集保存图片异常",
                    throwable = e,
                    extras = mapOf(
                        "faceQuality" to face.quality,
                        "faceWidth" to face.boundingBox.width(),
                        "faceHeight" to face.boundingBox.height(),
                    ),
                )
                applyTransition(
                    ManualFaceCaptureStateTransitions.onSaveError(
                        _uiState.value,
                        textResolver.text(R.string.manual_face_save_failed),
                    ),
                )
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
            facePipelineDelegate.getFaceQualityHints(face, capturedPhoto).map { hint ->
                textResolver.text(hint.messageRes)
            }
        } else {
            emptyList()
        }
    }

    override fun onCleared() {
        facePipelineDelegate.release()
    }

    private fun applyTransition(transition: ManualFaceCaptureTransition) {
        _uiState.value = transition.uiState
        transition.state?.let { _currentState.value = it }
    }

    private companion object {
        const val FACE_CAPTURE_DIAGNOSTIC_CATEGORY = "face_capture"
    }
}

internal enum class ManualFaceCaptureFailureStage(val diagnosticCode: String) {
    IMAGE_PROCESSING("process_image"),
    CAMERA_CAPTURE("capture"),
}

internal enum class ManualFaceCaptureFailure(@param:StringRes val messageRes: Int) {
    IMAGE_PROCESSING(R.string.manual_face_image_processing_failed),
    CAMERA_CAPTURE(R.string.manual_face_capture_failed),
}
