package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.ytone.longcare.common.utils.logE

internal class FaceImageExtractor {

    fun cropFaceFromImage(imageProxy: ImageProxy, boundingBox: Rect): Bitmap? {
        return try {
            val fullBitmap = imageProxyToBitmap(imageProxy) ?: return null
            val rotatedBitmap = rotateFaceBitmap(fullBitmap, imageProxy.imageInfo.rotationDegrees)
            val cropRect = buildExpandedFaceRect(
                boundingBox = boundingBox,
                bitmapWidth = rotatedBitmap.width,
                bitmapHeight = rotatedBitmap.height
            )

            val croppedBitmap = Bitmap.createBitmap(
                rotatedBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )
            val optimizedBitmap = optimizeFaceBitmapSize(croppedBitmap)

            if (rotatedBitmap != fullBitmap && rotatedBitmap != optimizedBitmap) {
                rotatedBitmap.recycle()
            }
            if (fullBitmap != optimizedBitmap) {
                fullBitmap.recycle()
            }

            optimizedBitmap
        } catch (e: Exception) {
            logE("Error cropping face from image", tag = "FaceCaptureAnalyzer", throwable = e)
            null
        }
    }
}
