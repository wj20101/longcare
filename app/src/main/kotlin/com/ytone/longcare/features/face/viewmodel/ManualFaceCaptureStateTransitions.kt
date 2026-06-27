package com.ytone.longcare.features.face.viewmodel

import android.graphics.Bitmap
import com.ytone.longcare.features.face.ui.DetectedFace
import com.ytone.longcare.features.face.ui.ManualFaceCaptureState
import com.ytone.longcare.features.face.ui.ManualFaceCaptureUiState

internal data class ManualFaceCaptureTransition(
    val uiState: ManualFaceCaptureUiState,
    val state: ManualFaceCaptureState? = null
)

internal object ManualFaceCaptureStateTransitions {

    fun onCameraPermissionChanged(
        currentUiState: ManualFaceCaptureUiState,
        granted: Boolean
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(cameraPermissionGranted = granted),
        state = if (granted) ManualFaceCaptureState.CameraReady else null
    )

    fun onStartCapture(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = currentUiState.copy(
                isLoading = true,
                errorMessage = null
            ),
            state = ManualFaceCaptureState.CapturingPhoto
        )

    fun onPhotoCaptured(
        currentUiState: ManualFaceCaptureUiState,
        bitmap: Bitmap
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            capturedPhoto = bitmap,
            isLoading = false
        ),
        state = ManualFaceCaptureState.ProcessingFaces
    )

    fun onPhotoCaptureError(
        currentUiState: ManualFaceCaptureUiState,
        message: String
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            isLoading = false,
            isProcessingFaces = false,
            errorMessage = message
        ),
        state = ManualFaceCaptureState.Error(message)
    )

    fun onDetectionStarted(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = currentUiState.copy(isProcessingFaces = true)
        )

    fun onFacesDetected(
        currentUiState: ManualFaceCaptureUiState,
        faces: List<DetectedFace>
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            detectedFaces = faces,
            isProcessingFaces = false
        )
    )

    fun onNoFacesDetected(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = currentUiState.copy(errorMessage = "未检测到人脸，请重新拍照"),
            state = ManualFaceCaptureState.NoFacesDetected
        )

    fun onMultipleFacesDetected(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = currentUiState,
            state = ManualFaceCaptureState.FacesDetected
        )

    fun onDetectionError(
        currentUiState: ManualFaceCaptureUiState,
        message: String
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            isProcessingFaces = false,
            errorMessage = "人脸检测失败: $message"
        ),
        state = ManualFaceCaptureState.Error(message.ifBlank { "人脸检测异常" })
    )

    fun onFaceSelected(
        currentUiState: ManualFaceCaptureUiState,
        index: Int
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            selectedFaceIndex = index,
            showConfirmationDialog = true
        ),
        state = ManualFaceCaptureState.FaceSelected
    )

    fun showConfirmationDialog(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(uiState = currentUiState.copy(showConfirmationDialog = true))

    fun hideConfirmationDialog(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(uiState = currentUiState.copy(showConfirmationDialog = false))

    fun onSaveStarted(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = currentUiState.copy(isLoading = true),
            state = ManualFaceCaptureState.SavingFace
        )

    fun onSaveSuccess(
        currentUiState: ManualFaceCaptureUiState,
        savedPath: String
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            savedFaceImagePath = savedPath,
            isLoading = false
        ),
        state = ManualFaceCaptureState.Success
    )

    fun onSaveError(
        currentUiState: ManualFaceCaptureUiState,
        message: String
    ): ManualFaceCaptureTransition = ManualFaceCaptureTransition(
        uiState = currentUiState.copy(
            isLoading = false,
            errorMessage = "保存人脸图片失败: $message"
        ),
        state = ManualFaceCaptureState.Error(message.ifBlank { "保存失败" })
    )

    fun onReset(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(
            uiState = ManualFaceCaptureUiState(
                cameraPermissionGranted = currentUiState.cameraPermissionGranted
            ),
            state = if (currentUiState.cameraPermissionGranted) {
                ManualFaceCaptureState.CameraReady
            } else {
                ManualFaceCaptureState.Idle
            }
        )

    fun onClearError(currentUiState: ManualFaceCaptureUiState): ManualFaceCaptureTransition =
        ManualFaceCaptureTransition(uiState = currentUiState.copy(errorMessage = null))
}
