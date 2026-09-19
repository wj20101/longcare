package com.ytone.longcare.features.photoupload.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.createBitmap
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker

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
