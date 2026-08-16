package com.ytone.longcare.features.facecapture

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

internal class FaceCaptureQualityEvaluator {

    fun calculate(face: Face): Float {
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0f
        val eyeScore = (leftEyeOpen + rightEyeOpen) / 2f
        return (calculatePositionQuality(face) * 0.7f + eyeScore * 0.3f)
            .coerceIn(0f, 1f)
    }

    fun calculatePositionQuality(face: Face): Float {
        val headAngleScore = when {
            abs(face.headEulerAngleY) <= 8.0 && abs(face.headEulerAngleZ) <= 6.0 -> 1.0f
            abs(face.headEulerAngleY) <= MAX_YAW_DEGREES &&
                abs(face.headEulerAngleZ) <= MAX_ROLL_DEGREES -> 0.7f
            else -> 0.2f
        }

        val faceSize = face.boundingBox.width() * face.boundingBox.height()
        val sizeScore = when {
            faceSize > 60_000 -> 1.0f
            faceSize > 45_000 -> 0.8f
            faceSize >= MINIMUM_FACE_AREA -> 0.6f
            faceSize > 20_000 -> 0.4f
            else -> 0.2f
        }

        return (headAngleScore * 0.65f + sizeScore * 0.35f).coerceIn(0f, 1f)
    }

    fun isPositionQualified(face: Face): Boolean = getPositionHint(face) == null

    fun isCaptureReady(face: Face): Boolean =
        isPositionQualified(face) &&
            (face.leftEyeOpenProbability ?: 0f) >= FaceBlinkGate.OPEN_EYE_THRESHOLD &&
            (face.rightEyeOpenProbability ?: 0f) >= FaceBlinkGate.OPEN_EYE_THRESHOLD

    fun getPositionHint(face: Face): String? = when {
        abs(face.headEulerAngleY) > MAX_YAW_DEGREES -> "请正对摄像头"
        abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES -> "请保持头部水平"
        face.boundingBox.width() * face.boundingBox.height() < MINIMUM_FACE_AREA -> "请靠近一些"
        else -> null
    }

    fun getCaptureHint(face: Face): String {
        val positionHint = getPositionHint(face)
        return when {
            positionHint != null -> positionHint
            (face.leftEyeOpenProbability ?: 0f) < FaceBlinkGate.OPEN_EYE_THRESHOLD ||
                (face.rightEyeOpenProbability ?: 0f) < FaceBlinkGate.OPEN_EYE_THRESHOLD ->
                "请睁开双眼后重试"
            else -> "请保持当前姿势"
        }
    }

    private companion object {
        const val MAX_YAW_DEGREES = 15.0
        const val MAX_ROLL_DEGREES = 10.0
        const val MINIMUM_FACE_AREA = 30_000
    }
}
