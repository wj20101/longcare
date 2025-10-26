package com.ytone.longcare.features.face.ui

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * 手动人脸捕获UI状态
 */
data class ManualFaceCaptureUiState(
    val isLoading: Boolean = false,
    val capturedPhoto: Bitmap? = null,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val selectedFaceIndex: Int? = null,
    val savedFaceImagePath: String? = null,
    val errorMessage: String? = null,
    val isProcessingFaces: Boolean = false,
    val showConfirmationDialog: Boolean = false,
    val cameraPermissionGranted: Boolean = false
)

/**
 * 检测到的人脸信息
 */
data class DetectedFace(
    val boundingBox: Rect,
    val croppedFace: Bitmap,
    val quality: Float,
    val confidence: Float
)

/**
 * 手动人脸捕获的各种状态
 */
sealed class ManualFaceCaptureState {
    object Idle : ManualFaceCaptureState()
    object CameraReady : ManualFaceCaptureState()
    object CapturingPhoto : ManualFaceCaptureState()
    object ProcessingFaces : ManualFaceCaptureState()
    object FacesDetected : ManualFaceCaptureState()
    object NoFacesDetected : ManualFaceCaptureState()
    object FaceSelected : ManualFaceCaptureState()
    object SavingFace : ManualFaceCaptureState()
    object Success : ManualFaceCaptureState()
    data class Error(val message: String) : ManualFaceCaptureState()
}

/**
 * 人脸质量评估结果
 */
data class FaceQualityResult(
    val quality: Float,
    val isGoodQuality: Boolean,
    val hints: List<String>
)