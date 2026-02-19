package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW

private object FaceCaptureLog

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
internal fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val image = imageProxy.image
        if (image == null) {
            FaceCaptureLog.logW("Image is null", tag = "FaceCaptureAnalyzer")
            return null
        }

        try {
            val format = image.format
            FaceCaptureLog.logD("Converting image format: $format", tag = "FaceCaptureAnalyzer")

            when (format) {
                ImageFormat.YUV_420_888 -> convertYuv420ToBitmap(image)
                ImageFormat.JPEG -> convertJpegToBitmap(image)
                else -> convertUnsupportedFormatBitmap(imageProxy, format)
            }
        } catch (e: IllegalStateException) {
            FaceCaptureLog.logW("Image state error: ${e.message}", tag = "FaceCaptureAnalyzer")
            null
        }
    } catch (e: Exception) {
        FaceCaptureLog.logE("Error converting ImageProxy to Bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
        null
    }
}

internal fun rotateFaceBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    return if (rotationDegrees == 0) {
        bitmap
    } else {
        try {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: Exception) {
            FaceCaptureLog.logE("Error rotating bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
            bitmap
        }
    }
}

internal fun optimizeFaceBitmapSize(
    bitmap: Bitmap,
    targetSize: Int = 512
): Bitmap {
    if (bitmap.width <= targetSize && bitmap.height <= targetSize) return bitmap

    val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaledWidth = (bitmap.width * scale).toInt()
    val scaledHeight = (bitmap.height * scale).toInt()

    val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight, true)
    if (scaledBitmap != bitmap) {
        bitmap.recycle()
    }
    return scaledBitmap
}

internal fun buildExpandedFaceRect(
    boundingBox: Rect,
    bitmapWidth: Int,
    bitmapHeight: Int,
    expandRatio: Float = 2.0f
): Rect {
    val newWidth = (boundingBox.width() * expandRatio).toInt()
    val newHeight = (boundingBox.height() * expandRatio).toInt()
    val centerX = boundingBox.centerX()
    val centerY = boundingBox.centerY()

    val newLeft = (centerX - newWidth / 2).coerceAtLeast(0)
    val newTop = (centerY - newHeight / 2).coerceAtLeast(0)
    val finalWidth = newWidth.coerceAtMost(bitmapWidth - newLeft)
    val finalHeight = newHeight.coerceAtMost(bitmapHeight - newTop)
    return Rect(newLeft, newTop, newLeft + finalWidth, newTop + finalHeight)
}

private fun convertJpegToBitmap(image: android.media.Image): Bitmap? {
    if (image.planes.isEmpty()) {
        FaceCaptureLog.logW("JPEG image has no planes", tag = "FaceCaptureAnalyzer")
        return null
    }
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun convertUnsupportedFormatBitmap(imageProxy: ImageProxy, format: Int): Bitmap? {
    return try {
        imageProxy.toBitmap()
    } catch (e: Exception) {
        FaceCaptureLog.logW(
            "Failed to convert image format $format",
            tag = "FaceCaptureAnalyzer",
            throwable = e
        )
        null
    }
}
