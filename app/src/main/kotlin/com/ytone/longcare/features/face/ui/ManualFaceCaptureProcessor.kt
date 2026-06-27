package com.ytone.longcare.features.face.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.face.viewmodel.ManualFaceCaptureViewModel
import java.util.concurrent.Executor

internal fun takeManualFacePhoto(
    imageCapture: ImageCapture?,
    executor: Executor,
    viewModel: ManualFaceCaptureViewModel
) {
    imageCapture?.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val originalBitmap = imageProxyToBitmap(image)
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val correctedBitmap = correctImageOrientation(originalBitmap, rotationDegrees)
                    viewModel.onPhotoCaptured(correctedBitmap)
                } catch (e: Exception) {
                    KLogger.e("CameraCapture", "图片处理失败", e)
                    viewModel.onPhotoCaptureFailed(
                        stage = "process_image",
                        messagePrefix = "人脸图片处理失败",
                        error = e,
                    )
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                KLogger.e("CameraCapture", "拍照失败", exception)
                viewModel.onPhotoCaptureFailed(
                    stage = "capture",
                    messagePrefix = "拍照失败",
                    error = exception,
                )
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val planeProxy = image.planes[0]
    val buffer: java.nio.ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
    options.inJustDecodeBounds = false
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    options.inMutable = true

    val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalStateException("无法解码图片")

    return ensureSoftwareBitmap(decodedBitmap)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

private fun correctImageOrientation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())

    return try {
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        ).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    } catch (e: Exception) {
        KLogger.e("ImageCorrection", "图片方向修正失败", e)
        bitmap
    }
}

private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
    val needsCopy = when {
        bitmap.config == Bitmap.Config.ARGB_8888 -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE -> true
        else -> true
    }

    if (!needsCopy) {
        return bitmap
    }

    return bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { copiedBitmap ->
        if (copiedBitmap != bitmap) {
            bitmap.recycle()
        }
    } ?: bitmap
}
