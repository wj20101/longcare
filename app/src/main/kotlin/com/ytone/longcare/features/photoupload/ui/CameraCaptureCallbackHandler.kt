package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun createImageSavedCallback(
    context: Context,
    scope: CoroutineScope,
    tempFile: File,
    watermarkBitmap: Bitmap,
    startPx: Float,
    bottomPx: Float,
    isFrontCamera: Boolean,
    onImageCaptured: (File) -> Unit,
    onError: () -> Unit
): ImageCapture.OnImageSavedCallback {
    return object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            scope.launch(Dispatchers.IO) {
                try {
                    val finalFile = processCapturedImageToFile(
                        context = context,
                        tempFile = tempFile,
                        watermarkBitmap = watermarkBitmap,
                        startPx = startPx,
                        bottomPx = bottomPx,
                        isFrontCamera = isFrontCamera,
                        onCleanupFailure = { cleanupError ->
                            CameraEventTracker.trackError(
                                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                cleanupError,
                                mapOf("reason" to "图片资源回收失败")
                            )
                        }
                    )

                    if (finalFile == null) {
                        CameraEventTracker.trackError(
                            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                            null,
                            mapOf(
                                "reason" to "图片处理未生成文件",
                                "tempFileExists" to tempFile.exists(),
                                "tempFileSize" to tempFile.takeIf { it.exists() }?.length()
                            )
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                            onError()
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        onImageCaptured(finalFile)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CameraEventTracker.trackError(
                        CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                        e,
                        mapOf("reason" to "图片处理异常: ${e.message ?: e.javaClass.simpleName}")
                    )
                    val detail = e.message ?: "请重新拍摄后重试"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片处理失败: $detail", Toast.LENGTH_SHORT).show()
                        onError()
                    }
                }
            }
        }

        override fun onError(exc: ImageCaptureException) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.CAPTURE_ERROR,
                exc,
                mapOf("reason" to "拍照保存失败: ${exc.message ?: exc.javaClass.simpleName}")
            )
            scope.launch(Dispatchers.Main) {
                val detail = exc.message ?: "请重新拍摄后重试"
                Toast.makeText(context, "拍照失败: $detail", Toast.LENGTH_SHORT).show()
                onError()
            }
        }
    }
}
