package com.ytone.longcare.features.identification.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

internal object FaceImageValidation {
    fun requireSupportedFaceImageBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw IOException("Face image bytes must not be empty.")
        }
        val decodeOptions = decodeImageBounds(bytes)
        val mimeType = decodeOptions.outMimeType ?: bytes.supportedMimeTypeFromHeader()
        if (
            decodeOptions.outWidth <= 0 ||
            decodeOptions.outHeight <= 0 ||
            mimeType !in SUPPORTED_IMAGE_MIME_TYPES
        ) {
            throw IOException("Face image format is unsupported.")
        }
        requireDecodableImage(bytes, decodeOptions)
    }

    private fun decodeImageBounds(bytes: ByteArray): BitmapFactory.Options {
        return try {
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
            }
        } catch (e: RuntimeException) {
            throw IOException("Face image format is unsupported.", e)
        }
    }

    private fun requireDecodableImage(
        bytes: ByteArray,
        bounds: BitmapFactory.Options,
    ) {
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateValidationSampleSize(bounds)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: RuntimeException) {
            throw IOException("Face image content is not decodable.", e)
        } ?: throw IOException("Face image content is not decodable.")
        bitmap.recycle()
    }

    private fun calculateValidationSampleSize(bounds: BitmapFactory.Options): Int {
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > VALIDATION_DECODE_MAX_DIMENSION ||
            bounds.outHeight / sampleSize > VALIDATION_DECODE_MAX_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private val SUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/bmp",
        "image/x-ms-bmp",
    )

    private const val VALIDATION_DECODE_MAX_DIMENSION = 128
}

private fun ByteArray.supportedMimeTypeFromHeader(): String? {
    return when {
        isJpeg() -> "image/jpeg"
        isPng() -> "image/png"
        isBmp() -> "image/bmp"
        else -> null
    }
}

private fun ByteArray.isJpeg(): Boolean {
    return size >= 3 &&
        this[0] == 0xFF.toByte() &&
        this[1] == 0xD8.toByte() &&
        this[2] == 0xFF.toByte()
}

private fun ByteArray.isPng(): Boolean {
    val signature = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    return size >= signature.size && copyOfRange(0, signature.size).contentEquals(signature)
}

private fun ByteArray.isBmp(): Boolean {
    return size >= 2 && this[0] == 0x42.toByte() && this[1] == 0x4D.toByte()
}
