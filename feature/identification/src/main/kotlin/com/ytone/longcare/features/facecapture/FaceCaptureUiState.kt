package com.ytone.longcare.features.facecapture

/** 单次相机人脸采集阶段。 */
enum class FaceCapturePhase {
    STARTING,
    PREPARING,
    SCANNING,
    CONFIRMING,
    CAPTURED,
}

/** 单次相机人脸采集状态，不暴露照片选择或相册入口。 */
data class FaceCaptureUiState(
    val phase: FaceCapturePhase = FaceCapturePhase.STARTING,
    val countdownSeconds: Int = 0,
    val captureReady: Boolean = false,
    val userHint: String = "请将面部置于取景框内",
    val faceDetected: Boolean = false,
    val faceQuality: Float = 0f,
    val confirmationProgress: Float = 0f,
) {
    val isDetectionEnabled: Boolean
        get() = phase == FaceCapturePhase.SCANNING || phase == FaceCapturePhase.CONFIRMING
}

internal const val PREPARATION_COUNTDOWN_SECONDS = 3
