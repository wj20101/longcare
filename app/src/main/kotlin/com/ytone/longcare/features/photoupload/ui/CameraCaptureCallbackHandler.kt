package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import com.ytone.longcare.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun createImageSavedCallback(
    context: Context,
    scope: CoroutineScope,
    tempFile: File,
    watermarkBitmap: Bitmap,
    startPx: Float,
    bottomPx: Float,
    isFrontCamera: Boolean,
    processCapturedImage: suspend (WatermarkedCaptureRequest) -> File,
    onImageCaptured: (File) -> Unit,
    onError: () -> Unit
): ImageCapture.OnImageSavedCallback {
    return object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val processingJob = scope.launch {
                try {
                    val finalFile = processCapturedImage(
                        WatermarkedCaptureRequest(
                            temporaryCaptureFile = tempFile,
                            watermarkBitmap = watermarkBitmap,
                            watermarkStartPx = startPx,
                            watermarkBottomPx = bottomPx,
                            mirrorHorizontally = isFrontCamera,
                        )
                    )
                    onImageCaptured(finalFile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CameraEventTracker.trackError(
                        CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                        e,
                        mapOf("reason" to "图片处理异常: ${e.message ?: e.javaClass.simpleName}")
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.camera_image_processing_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onError()
                }
            }
            processingJob.invokeOnCompletion {
                cleanupCaptureArtifacts(tempFile, watermarkBitmap)
            }
        }

        override fun onError(exc: ImageCaptureException) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.CAPTURE_ERROR,
                exc,
                mapOf("reason" to "拍照保存失败: ${exc.message ?: exc.javaClass.simpleName}")
            )
            cleanupCaptureArtifacts(tempFile, watermarkBitmap)
            scope.launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.camera_capture_save_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                onError()
            }
        }
    }
}

internal fun cleanupCaptureArtifacts(
    temporaryFile: File,
    watermarkBitmap: Bitmap,
) {
    if (temporaryFile.exists()) temporaryFile.delete()
    if (!watermarkBitmap.isRecycled) watermarkBitmap.recycle()
}
