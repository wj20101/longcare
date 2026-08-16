package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import android.graphics.Rect
import com.ytone.longcare.common.utils.logE

internal class FaceImageExtractor {

    /** Crops a face from an upright bitmap. The caller retains ownership of [source]. */
    fun cropFaceFromBitmap(source: Bitmap, boundingBox: Rect): Bitmap? {
        var croppedBitmap: Bitmap? = null
        return try {
            val cropRect = buildExpandedFaceRect(
                boundingBox = boundingBox,
                bitmapWidth = source.width,
                bitmapHeight = source.height,
            )

            croppedBitmap = Bitmap.createBitmap(
                source,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height(),
            )
            optimizeFaceBitmapSize(croppedBitmap)
        } catch (e: Exception) {
            croppedBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            logE("Error cropping face from image", tag = "FaceImageExtractor", throwable = e)
            null
        }
    }
}
