package com.ytone.longcare.features.facecapture

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

internal class FaceCaptureQualityEvaluator {

    fun calculate(face: Face): Float {
        var quality = 0f

        val headAngleScore = when {
            abs(face.headEulerAngleY) <= 8.0 && abs(face.headEulerAngleZ) <= 6.0 -> 1.0f
            abs(face.headEulerAngleY) <= 15.0 && abs(face.headEulerAngleZ) <= 10.0 -> 0.7f
            else -> 0.3f
        }
        quality += headAngleScore * 0.4f

        val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0f
        val eyeScore = (leftEyeOpen + rightEyeOpen) / 2f
        quality += eyeScore * 0.3f

        val faceSize = face.boundingBox.width() * face.boundingBox.height()
        val sizeScore = when {
            faceSize > 60000 -> 1.0f
            faceSize > 45000 -> 0.8f
            faceSize > 30000 -> 0.6f
            faceSize > 20000 -> 0.4f
            else -> 0.2f
        }
        quality += sizeScore * 0.2f

        val smileScore = face.smilingProbability ?: 0.5f
        quality += smileScore * 0.1f

        return quality.coerceIn(0f, 1f)
    }

    fun getHint(face: Face): String {
        return when {
            abs(face.headEulerAngleY) > 15.0 -> "请正对摄像头"
            abs(face.headEulerAngleZ) > 10.0 -> "请保持头部水平"
            (face.leftEyeOpenProbability ?: 0f) < 0.8f ||
                (face.rightEyeOpenProbability ?: 0f) < 0.8f -> "请睁开眼睛"
            face.boundingBox.width() * face.boundingBox.height() < 30000 -> "请靠近一些"
            else -> "请保持当前姿势"
        }
    }
}
