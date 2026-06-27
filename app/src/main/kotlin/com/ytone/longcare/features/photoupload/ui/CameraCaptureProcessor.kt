package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope

internal fun takePhoto(
    context: Context,
    cameraController: LifecycleCameraController,
    executor: Executor,
    watermarkView: View,
    isFrontCamera: Boolean,
    scope: CoroutineScope,
    onImageCaptured: (File) -> Unit,
    onError: () -> Unit
) {
    val captureStartTime = System.currentTimeMillis()

    val watermarkBitmap = try {
        viewToBitmapSafe(watermarkView)
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "水印视图捕获失败")
        )
        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
        onError()
        return
    }

    if (watermarkBitmap == null) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            null,
            mapOf("reason" to "水印Bitmap为null")
        )
        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
        onError()
        return
    }

    val density = context.resources.displayMetrics.density
    val startPx = (13 * density)
    val bottomPx = (14 * density)

    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    try {
        cameraController.takePicture(
            outputOptions,
            executor,
            createImageSavedCallback(
                context = context,
                scope = scope,
                tempFile = tempFile,
                watermarkBitmap = watermarkBitmap,
                startPx = startPx,
                bottomPx = bottomPx,
                isFrontCamera = isFrontCamera,
                onImageCaptured = onImageCaptured,
                onError = onError
            )
        )
    } catch (e: Exception) {
        val detail = e.message ?: "请检查相机权限后重试"
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.CAPTURE_ERROR,
            e,
            mapOf(
                "step" to "调用takePicture异常",
                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
            )
        )
        Toast.makeText(context, "调用相机失败: $detail", Toast.LENGTH_SHORT).show()
        onError()
    }
}
