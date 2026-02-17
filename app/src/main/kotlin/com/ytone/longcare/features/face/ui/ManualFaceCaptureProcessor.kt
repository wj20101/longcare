package com.ytone.longcare.features.face.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
                    com.ytone.longcare.common.utils.KLogger.e("CameraCapture", "图片处理失败", e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                com.ytone.longcare.common.utils.KLogger.e("CameraCapture", "拍照失败", exception)
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

    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalStateException("无法解码图片")
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
        com.ytone.longcare.common.utils.KLogger.e("ImageCorrection", "图片方向修正失败", e)
        bitmap
    }
}
