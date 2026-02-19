package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal suspend fun processCapturedImageToFile(
    context: Context,
    tempFile: File,
    watermarkBitmap: Bitmap,
    startPx: Float,
    bottomPx: Float,
    isFrontCamera: Boolean,
    onCleanupFailure: (Throwable) -> Unit
): File? {
    var bitmap: Bitmap? = null
    var watermarkedBitmap: Bitmap? = null

    return try {
        withTimeout(calculateDynamicTimeout(context)) {
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
                if (minOf(scaledWidth, scaledHeight) >= minTargetDimension) {
                    inSampleSize = nextSampleSize
                } else {
                    break
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            options.inMutable = true

            val decodedBitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options) ?: return@withTimeout null
            bitmap = decodedBitmap
            val currentBitmap = decodedBitmap
            val rotatedBitmap = rotateBitmapByExif(currentBitmap, tempFile.absolutePath)
            if (rotatedBitmap != null && rotatedBitmap != currentBitmap) {
                currentBitmap.recycle()
                bitmap = rotatedBitmap
            }

            tempFile.delete()

            var processedBitmap = requireNotNull(bitmap)
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
            watermarkedBitmap = addWatermarkSafe(processedBitmap, watermarkBitmap, startPx, bottomPx)
            if (watermarkedBitmap == null) {
                return@withTimeout null
            }

            if (processedBitmap != watermarkedBitmap) {
                processedBitmap.recycle()
            }
            bitmap = null
            watermarkBitmap.recycle()

            ensureActive()
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val finalFile = File(storageDir, "captured_image_${System.currentTimeMillis()}.jpg")
            val finalBitmap = requireNotNull(watermarkedBitmap)

            FileOutputStream(finalFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            finalBitmap.recycle()
            watermarkedBitmap = null
            finalFile
        }
    } finally {
        runCatching {
            bitmap?.recycle()
            watermarkedBitmap?.recycle()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }.onFailure(onCleanupFailure)
    }
}
