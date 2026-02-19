package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import java.io.ByteArrayOutputStream

private object FaceCaptureYuvLog

internal fun convertYuv420ToBitmap(image: android.media.Image): Bitmap? {
    return try {
        val nv21 = buildNv21Data(image) ?: return null
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        FaceCaptureYuvLog.logE("Error converting YUV to Bitmap", tag = "FaceCaptureAnalyzer", throwable = e)
        null
    }
}

private fun buildNv21Data(image: android.media.Image): ByteArray? {
    if (image.planes.size < 3) {
        FaceCaptureYuvLog.logW("Invalid planes count: ${image.planes.size}", tag = "FaceCaptureAnalyzer")
        return null
    }

    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    if (ySize <= 0 || uSize <= 0 || vSize <= 0) {
        FaceCaptureYuvLog.logW("Invalid buffer sizes: Y=$ySize, U=$uSize, V=$vSize", tag = "FaceCaptureAnalyzer")
        return null
    }

    return ByteArray(ySize + uSize + vSize).also { nv21 ->
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
    }
}
