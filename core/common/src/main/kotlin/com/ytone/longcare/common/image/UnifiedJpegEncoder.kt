package com.ytone.longcare.common.image

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException

data class EncodedJpeg(
    val bytes: ByteArray,
    val quality: Int,
)

/** Encodes every app-owned JPEG through the same validated policy. */
object UnifiedJpegEncoder {
    @Throws(IOException::class)
    fun encode(
        bitmap: Bitmap,
        policy: ImageProcessingPolicy,
    ): EncodedJpeg {
        var quality = policy.initialJpegQuality

        while (true) {
            val bytes =
                ByteArrayOutputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        throw IOException("JPEG compression failed.")
                    }
                    output.toByteArray()
                }
            if (bytes.isEmpty()) {
                throw IOException("JPEG compression produced an empty image.")
            }
            if (bytes.size <= policy.maxOutputBytes) {
                return EncodedJpeg(bytes = bytes, quality = quality)
            }
            if (quality <= policy.minimumJpegQuality) {
                throw IOException(
                    "Compressed image exceeds ${policy.maxOutputBytes} bytes at minimum quality."
                )
            }
            quality =
                (quality - policy.jpegQualityStep)
                    .coerceAtLeast(policy.minimumJpegQuality)
        }
    }
}
