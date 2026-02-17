package com.ytone.longcare.features.photoupload.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        var bitmap: Bitmap? = null
                        var watermarkedBitmap: Bitmap? = null

                        try {
                            val timeoutMs = calculateDynamicTimeout(context)
                            withTimeout(timeoutMs) {
                                ensureActive()

                                val options = BitmapFactory.Options()
                                options.inJustDecodeBounds = true
                                BitmapFactory.decodeFile(tempFile.absolutePath, options)

                                val minTargetDimension = 1080
                                var inSampleSize = 1

                                while (true) {
                                    val nextSampleSize = inSampleSize * 2
                                    val scaledWidth = options.outWidth / nextSampleSize
                                    val scaledHeight = options.outHeight / nextSampleSize
                                    val scaledMinDimension = minOf(scaledWidth, scaledHeight)
                                    if (scaledMinDimension >= minTargetDimension) {
                                        inSampleSize = nextSampleSize
                                    } else {
                                        break
                                    }
                                }

                                options.inJustDecodeBounds = false
                                options.inSampleSize = inSampleSize
                                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                                options.inMutable = true

                                bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)

                                if (bitmap == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "图片读取失败，请重试", Toast.LENGTH_SHORT).show()
                                        onError()
                                    }
                                    return@withTimeout
                                }

                                val currentBitmap = bitmap
                                val rotatedBitmap = rotateBitmapByExif(currentBitmap, tempFile.absolutePath)
                                if (rotatedBitmap != null && rotatedBitmap != currentBitmap) {
                                    currentBitmap.recycle()
                                    bitmap = rotatedBitmap
                                }

                                tempFile.delete()

                                var processedBitmap = bitmap
                                if (isFrontCamera) {
                                    val flipped = flipBitmapHorizontallySafe(processedBitmap)
                                    if (flipped != null) {
                                        if (processedBitmap != flipped) {
                                            processedBitmap.recycle()
                                        }
                                        processedBitmap = flipped
                                        bitmap = null
                                    }
                                }

                                ensureActive()

                                watermarkedBitmap =
                                    addWatermarkSafe(processedBitmap, watermarkBitmap, startPx, bottomPx)

                                if (watermarkedBitmap == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
                                        onError()
                                    }
                                    return@withTimeout
                                }

                                if (processedBitmap != watermarkedBitmap) {
                                    processedBitmap.recycle()
                                }
                                bitmap = null
                                watermarkBitmap.recycle()

                                ensureActive()
                                val storageDir =
                                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                                val finalFile = File(
                                    storageDir,
                                    "captured_image_${System.currentTimeMillis()}.jpg"
                                )

                                FileOutputStream(finalFile).use { out ->
                                    val finalBitmap = watermarkedBitmap ?: return@use
                                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }

                                watermarkedBitmap.recycle()
                                watermarkedBitmap = null

                                withContext(Dispatchers.Main) {
                                    onImageCaptured(finalFile)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            CameraEventTracker.trackError(
                                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                e,
                                mapOf("reason" to "图片处理失败: ${e.message}")
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                onError()
                            }
                        } finally {
                            runCatching {
                                bitmap?.recycle()
                                watermarkedBitmap?.recycle()
                                if (tempFile.exists()) tempFile.delete()
                            }.onFailure { cleanupError ->
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                    cleanupError,
                                    mapOf("reason" to "图片资源回收失败")
                                )
                            }
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    CameraEventTracker.trackError(
                        CameraEventTracker.EventType.CAPTURE_ERROR,
                        exc,
                        mapOf("reason" to "拍照保存失败: ${exc.message}")
                    )
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                        onError()
                    }
                }
            }
        )
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.CAPTURE_ERROR,
            e,
            mapOf(
                "step" to "调用takePicture异常",
                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
            )
        )
        Toast.makeText(context, "调用相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        onError()
    }
}

private fun flipBitmapHorizontallySafe(bitmap: Bitmap): Bitmap? {
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

private fun viewToBitmapSafe(view: View): Bitmap? {
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

private fun addWatermarkSafe(
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

private fun calculateDynamicTimeout(context: Context): Long {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryClass = activityManager?.memoryClass ?: 128

    return when {
        memoryClass >= 256 -> 15_000L
        memoryClass >= 128 -> 25_000L
        else -> 35_000L
    }
}

private fun rotateBitmapByExif(bitmap: Bitmap, filePath: String): Bitmap? {
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
