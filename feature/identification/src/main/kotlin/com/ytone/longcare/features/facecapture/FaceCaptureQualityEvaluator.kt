package com.ytone.longcare.features.facecapture

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

internal class FaceCaptureQualityEvaluator {
    fun isPositionQualified(face: Face): Boolean = getPositionHint(face) == null

    fun isCaptureReady(face: Face): Boolean = isPositionQualified(face)

    fun getPositionHint(face: Face): FaceCaptureHint? = when {
        abs(face.headEulerAngleY) > MAX_YAW_DEGREES -> FaceCaptureHint.FACE_FORWARD
        abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES -> FaceCaptureHint.HEAD_LEVEL
        face.boundingBox.width() * face.boundingBox.height() < MINIMUM_FACE_AREA -> FaceCaptureHint.MOVE_CLOSER
        else -> null
    }

    fun getCaptureHint(face: Face): FaceCaptureHint =
        getPositionHint(face) ?: FaceCaptureHint.HOLD_POSE

    private companion object {
        const val MAX_YAW_DEGREES = 15.0
        const val MAX_ROLL_DEGREES = 10.0
        const val MINIMUM_FACE_AREA = 30_000
    }
}
