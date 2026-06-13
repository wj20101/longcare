package com.ytone.longcare.features.identification.data

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

class RemoteFaceImageDownloader(
    private val callFactory: Call.Factory,
) {
    @Inject constructor() : this(defaultCallFactory())

    fun downloadToFile(url: String, destinationFile: File): File {
        val request = buildRequest(url)
        val destinationDir = destinationFile.parentFile
            ?: throw IOException("Face image download file must have a parent directory.")
        val tempFile = File(destinationDir, "${destinationFile.name}.tmp")
        try {
            if (destinationFile.exists()) {
                throw IOException("Face image download file already exists.")
            }

            callFactory.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Face image download failed with HTTP ${response.code}.")
                }

                if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                    throw IOException("Failed to create face image download directory.")
                }
                if (tempFile.exists() && !tempFile.delete()) {
                    throw IOException("Failed to replace temporary face image download file.")
                }

                tempFile.sink().buffer().use { sink ->
                    sink.writeAll(response.body.source())
                }
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = false)
                tempFile.deleteIfExists()
            }
            return destinationFile
        } catch (e: Exception) {
            tempFile.deleteIfExists(suppressedBy = e)
            throw e
        }
    }

    private fun File.deleteIfExists(suppressedBy: Exception? = null) {
        if (!exists() || delete()) return

        val exception = IOException("Failed to remove temporary face image download file.")
        if (suppressedBy == null) {
            throw exception
        } else {
            suppressedBy.addSuppressed(exception)
        }
    }

    private fun buildRequest(url: String): Request {
        return try {
            Request.Builder()
                .url(url)
                .get()
                .build()
        } catch (exception: Exception) {
            throw IOException("Invalid face image URL.", exception)
        }
    }

    companion object {
        private fun defaultCallFactory(): Call.Factory {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
