package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import com.ytone.longcare.common.utils.logE

private object FaceCaptureLog

internal fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        // CameraX owns the YUV/JPEG conversion and correctly honors every plane's
        // rowStride/pixelStride. Manually concatenating Y/U/V planes corrupts frames on
        // devices whose camera buffers contain row padding or interleaved chroma data.
        imageProxy.toBitmap()
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
