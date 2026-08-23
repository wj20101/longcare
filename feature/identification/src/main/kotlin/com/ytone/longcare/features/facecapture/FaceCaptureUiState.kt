package com.ytone.longcare.features.facecapture

import androidx.annotation.StringRes
import com.ytone.longcare.feature.identification.R

/** 单次相机人脸采集阶段。 */
enum class FaceCapturePhase {
    STARTING,
    PREPARING,
    SCANNING,
    CONFIRMING,
    CAPTURING,
    CAPTURED,
}

enum class FaceCaptureHint(@param:StringRes val messageRes: Int) {
    CENTER_FACE(R.string.face_capture_hint_center_face),
    PREPARE(R.string.face_capture_hint_prepare),
    OPEN_EYES_FACING_CAMERA(R.string.face_capture_hint_open_eyes_facing_camera),
    BLINK_CAPTURED(R.string.face_capture_hint_blink_captured),
    SUCCESS(R.string.face_capture_hint_success),
    NO_FACE(R.string.face_capture_hint_no_face),
    SINGLE_PERSON(R.string.face_capture_hint_single_person),
    HOLD_FOR_CONFIRMATION(R.string.face_capture_hint_hold_for_confirmation),
    DETECTION_FAILED(R.string.face_capture_hint_detection_failed),
    BLINK(R.string.face_capture_hint_blink),
    REOPEN_EYES(R.string.face_capture_hint_reopen_eyes),
    HOLD_AFTER_BLINK(R.string.face_capture_hint_hold_after_blink),
    FACE_FORWARD(R.string.face_capture_hint_face_forward),
    HEAD_LEVEL(R.string.face_capture_hint_head_level),
    MOVE_CLOSER(R.string.face_capture_hint_move_closer),
    OPEN_EYES_RETRY(R.string.face_capture_hint_open_eyes_retry),
    HOLD_POSE(R.string.face_capture_hint_hold_pose),
    PHOTO_PROCESSING_FAILED(R.string.face_capture_hint_photo_processing_failed),
    NO_FACE_IN_PHOTO(R.string.face_capture_hint_no_face_in_photo),
    MULTIPLE_FACES_IN_PHOTO(R.string.face_capture_hint_multiple_faces_in_photo),
    PHOTO_DETECTION_FAILED(R.string.face_capture_hint_photo_detection_failed),
    CAPTURE_FAILED(R.string.face_capture_hint_capture_failed),
}

/** 单次相机人脸采集状态，不暴露照片选择或相册入口。 */
data class FaceCaptureUiState(
    val phase: FaceCapturePhase = FaceCapturePhase.STARTING,
    val countdownSeconds: Int = 0,
    val captureReady: Boolean = false,
    val userHint: FaceCaptureHint = FaceCaptureHint.CENTER_FACE,
    val faceDetected: Boolean = false,
    val faceQuality: Float = 0f,
    val confirmationProgress: Float = 0f,
) {
    val isDetectionEnabled: Boolean
        get() = phase == FaceCapturePhase.SCANNING || phase == FaceCapturePhase.CONFIRMING

    val isStillCaptureRequested: Boolean
        get() = phase == FaceCapturePhase.CAPTURING
}

internal const val PREPARATION_COUNTDOWN_SECONDS = 3
