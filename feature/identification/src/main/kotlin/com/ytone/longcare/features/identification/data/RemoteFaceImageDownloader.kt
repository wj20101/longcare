package com.ytone.longcare.features.identification.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

class RemoteFaceImageDownloader(
    private val callFactory: Call.Factory,
) {
    @Inject constructor() : this(defaultCallFactory())

    fun download(url: String): ByteArray {
        val uri = parseAndValidateUri(url)
        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .build()
        callFactory.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Face image download failed with HTTP ${response.code}.")
            }
            val body = response.body
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
            if (contentType != null && contentType !in ALLOWED_CONTENT_TYPES && contentType != OCTET_STREAM) {
                throw IOException("Unsupported face image content type.")
            }

            val bytes = body.readBytesWithLimit(MAX_REMOTE_FACE_IMAGE_BYTES)
            if (!bytes.looksLikeSupportedImage()) {
                throw IOException("Downloaded face image format is unsupported.")
            }
            return bytes
        }
    }

    private fun parseAndValidateUri(url: String): URI {
        val uri = try {
            URI(url)
        } catch (exception: Exception) {
            throw IOException("Invalid face image URL.", exception)
        }
        if (uri.scheme?.lowercase() != HTTPS_SCHEME) {
            throw IOException("Face image URL must use HTTPS.")
        }
        if (uri.userInfo != null) {
            throw IOException("Face image URL must not contain user info.")
        }
        if (uri.port != -1 && uri.port != HTTPS_PORT) {
            throw IOException("Face image URL must use the default HTTPS port.")
        }
        val host = uri.host?.lowercase().orEmpty()
        if (!host.isTrustedCosHost()) {
            throw IOException("Face image URL host is not trusted.")
        }
        return uri
    }

    private fun ResponseBody.readBytesWithLimit(maxBytes: Long): ByteArray {
        val declaredLength = contentLength()
        if (declaredLength > maxBytes) {
            throw IOException("Face image is too large.")
        }

        val output = ByteArrayOutputStream()
        byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw IOException("Face image is too large.")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun String.isTrustedCosHost(): Boolean {
        return contains(".cos.") && (
            endsWith(".myqcloud.com") ||
                endsWith(".tencentcos.cn")
            )
    }

    private fun ByteArray.looksLikeSupportedImage(): Boolean {
        return isJpeg() || isPng() || isBmp()
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

    companion object {
        private const val HTTPS_SCHEME = "https"
        private const val HTTPS_PORT = 443
        private const val OCTET_STREAM = "application/octet-stream"
        private const val MAX_REMOTE_FACE_IMAGE_BYTES = 512L * 1024L
        private val ALLOWED_CONTENT_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/bmp",
            "image/x-ms-bmp",
        )

        private fun defaultCallFactory(): Call.Factory {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
