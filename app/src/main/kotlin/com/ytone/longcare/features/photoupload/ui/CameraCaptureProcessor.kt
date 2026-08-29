package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import com.ytone.longcare.R
import com.ytone.longcare.common.image.ManagedImageFile
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
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
    createTemporaryCaptureFile: () -> ManagedImageFile,
    processCapturedImage: suspend (WatermarkedCaptureRequest) -> File,
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
        Toast.makeText(
            context,
            context.getString(R.string.camera_watermark_failed),
            Toast.LENGTH_SHORT,
        ).show()
        onError()
        return
    }

    if (watermarkBitmap == null) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            null,
            mapOf("reason" to "水印Bitmap为null")
        )
        Toast.makeText(
            context,
            context.getString(R.string.camera_watermark_failed),
            Toast.LENGTH_SHORT,
        ).show()
        onError()
        return
    }

    val density = context.resources.displayMetrics.density
    val startPx = (13 * density)
    val bottomPx = (14 * density)

    val temporaryCapture = try {
        createTemporaryCaptureFile()
    } catch (error: Exception) {
        if (!watermarkBitmap.isRecycled) watermarkBitmap.recycle()
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.CAPTURE_ERROR,
            error,
            mapOf("reason" to "当前用户受管拍摄目录不可用"),
        )
        Toast.makeText(
            context,
            context.getString(R.string.camera_capture_failed),
            Toast.LENGTH_SHORT,
        ).show()
        onError()
        return
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(temporaryCapture.file).build()

    try {
        cameraController.takePicture(
            outputOptions,
            executor,
            createImageSavedCallback(
                context = context,
                scope = scope,
                temporaryCapture = temporaryCapture,
                watermarkBitmap = watermarkBitmap,
                startPx = startPx,
                bottomPx = bottomPx,
                isFrontCamera = isFrontCamera,
                processCapturedImage = processCapturedImage,
                onImageCaptured = onImageCaptured,
                onError = onError
            )
        )
    } catch (e: Exception) {
        cleanupCaptureArtifacts(temporaryCapture, watermarkBitmap)
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.CAPTURE_ERROR,
            e,
            mapOf(
                "step" to "调用takePicture异常",
                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
            )
        )
        Toast.makeText(
            context,
            context.getString(R.string.camera_capture_failed),
            Toast.LENGTH_SHORT,
        ).show()
        onError()
    }
}
