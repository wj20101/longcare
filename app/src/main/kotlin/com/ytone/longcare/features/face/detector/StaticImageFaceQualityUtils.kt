package com.ytone.longcare.features.face.detector

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import com.google.mlkit.vision.face.Face
import kotlin.math.max
import kotlin.math.min

internal fun cropFaceFromImage(bitmap: Bitmap, face: Face): Bitmap {
    val boundingBox = face.boundingBox
    val imageWidth = bitmap.width
    val imageHeight = bitmap.height

    val expandRatio = 0.3f
    val expandX = (boundingBox.width() * expandRatio).toInt()
    val expandY = (boundingBox.height() * expandRatio).toInt()

    val left = max(0, boundingBox.left - expandX)
    val top = max(0, boundingBox.top - expandY)
    val right = min(imageWidth, boundingBox.right + expandX)
    val bottom = min(imageHeight, boundingBox.bottom + expandY)

    val width = right - left
    val height = bottom - top
    require(width > 0 && height > 0) { "Invalid crop dimensions" }

    val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
    val targetSize = 512
    val scaledBitmap = croppedBitmap.scale(targetSize, targetSize, true)
    if (scaledBitmap != croppedBitmap) {
        croppedBitmap.recycle()
    }
    return scaledBitmap
}

internal fun calculateFaceQuality(
    face: Face,
    boundingBox: Rect,
    bitmap: Bitmap
): Float {
    val imageArea = bitmap.width * bitmap.height
    val faceArea = boundingBox.width() * boundingBox.height()
    val faceRatio = faceArea.toFloat() / imageArea.toFloat()

    val sizeScore = when {
        faceRatio > 0.15f -> 1.0f
        faceRatio > 0.10f -> 0.8f
        faceRatio > 0.05f -> 0.6f
        else -> 0.3f
    }
    val smileScore = face.smilingProbability?.let { if (it > 0.3f) 0.1f else 0.0f } ?: 0.0f
    val eyeScore = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability)
        .let { if (it.isNotEmpty() && it.all { p -> p > 0.5f }) 0.1f else 0.0f }

    return (sizeScore + smileScore + eyeScore).coerceIn(0.0f, 1.0f)
}

internal fun buildFaceQualityHints(
    quality: Float,
    faceRatio: Float
): List<String> {
    val hints = mutableListOf<String>()
    when {
        quality < 0.3f -> hints.add("人脸质量较差，请重新拍照")
        quality < 0.5f -> hints.add("人脸质量一般，建议重新拍照")
        quality < 0.7f -> hints.add("人脸质量良好")
        else -> hints.add("人脸质量优秀")
    }
    when {
        faceRatio < 0.05f -> hints.add("人脸太小，请靠近一些")
        faceRatio > 0.4f -> hints.add("人脸太大，请稍微远离")
    }
    return hints
}
