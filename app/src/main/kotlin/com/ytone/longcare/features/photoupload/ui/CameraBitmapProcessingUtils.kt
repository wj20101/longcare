package com.ytone.longcare.features.photoupload.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker

internal fun flipBitmapHorizontallySafe(bitmap: Bitmap): Bitmap? {
    return try {
        val matrix = Matrix()
        matrix.preScale(-1f, 1f)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during flip: ${e.message}", e),
            mapOf(
                "reason" to "翻转时内存不足",
                "bitmapSize" to "${bitmap.width}x${bitmap.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "图片翻转异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

internal fun viewToBitmapSafe(view: View): Bitmap? {
    return try {
        if (view.width <= 0 || view.height <= 0) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                null,
                mapOf(
                    "reason" to "视图尺寸无效",
                    "viewSize" to "${view.width}x${view.height}"
                )
            )
            return null
        }
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        bitmap
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during viewToBitmap: ${e.message}", e),
            mapOf(
                "reason" to "视图转Bitmap时内存不足",
                "viewSize" to "${view.width}x${view.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "视图转Bitmap异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

internal fun addWatermarkSafe(
    bitmap: Bitmap,
    watermark: Bitmap,
    startPx: Float,
    bottomPx: Float
): Bitmap? {
    return try {
        val result = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawBitmap(watermark, startPx, (bitmap.height - watermark.height - bottomPx), null)
        result
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during addWatermark: ${e.message}", e),
            mapOf(
                "reason" to "添加水印时内存不足",
                "bitmapSize" to "${bitmap.width}x${bitmap.height}",
                "watermarkSize" to "${watermark.width}x${watermark.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "添加水印异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

internal fun calculateDynamicTimeout(context: Context): Long {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryClass = activityManager?.memoryClass ?: 128

    return when {
        memoryClass >= 256 -> 15_000L
        memoryClass >= 128 -> 25_000L
        else -> 35_000L
    }
}

internal fun rotateBitmapByExif(bitmap: Bitmap, filePath: String): Bitmap? {
    return try {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationDegrees == 0f) {
            bitmap
        } else {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        }
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "EXIF旋转处理失败: ${e.javaClass.simpleName}")
        )
        bitmap
    }
}
