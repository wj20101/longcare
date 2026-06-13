package com.ytone.longcare.features.identification.data

import android.util.Base64
import java.io.File
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteFaceImageDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `download accepts https cos image response`() {
        val imageBytes = pngBytes()
        val destination = destinationFile("face.png")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/png".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.png",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download accepts http image response`() {
        val imageBytes = pngBytes()
        val destination = destinationFile("face.png")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/png".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "http://longcare-bucket.cos.ap-beijing.myqcloud.com/face.png",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download accepts image response from any host`() {
        val imageBytes = pngBytes()
        val destination = destinationFile("face.png")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/png".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://example.com/face.png",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download accepts image bytes with misleading content type`() {
        val imageBytes = pngBytes()
        val destination = destinationFile("face.png")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("text/plain".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://static.example.com/face.png",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download accepts server provided non image bytes`() {
        val bodyBytes = "not-image-content".toByteArray()
        val destination = destinationFile("face.jpg")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                bodyBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
            destinationFile = destination,
        )

        assertArrayEquals(bodyBytes, result.readBytes())
    }

    @Test
    fun `download accepts image larger than previous local limit`() {
        val imageBytes = bytesOfSize(512 * 1024 + 1)
        val destination = destinationFile("face.jpg")
        assertTrue(imageBytes.size > 512 * 1024)
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download accepts image above previous protective limit`() {
        val imageBytes = bytesOfSize(PREVIOUS_PROTECTIVE_LIMIT_BYTES + 1)
        val destination = destinationFile("face.jpg")
        assertTrue(imageBytes.size > PREVIOUS_PROTECTIVE_LIMIT_BYTES)
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        val result = downloader.downloadToFile(
            url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
            destinationFile = destination,
        )

        assertArrayEquals(imageBytes, result.readBytes())
    }

    @Test
    fun `download http failure does not create destination file`() {
        val destination = destinationFile("face.jpg")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient(responseCode = 500) {
                "server-error".toResponseBody("text/plain".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.downloadToFile(
                url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
                destinationFile = destination,
            )
        }
        assertFalse(destination.exists())
    }

    @Test
    fun `download refuses to overwrite existing destination file`() {
        val existingBytes = "existing-face-cache".toByteArray()
        val destination = temporaryFolder.newFile("face.jpg").apply {
            writeBytes(existingBytes)
        }
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                "new-face-cache".toResponseBody("image/jpeg".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.downloadToFile(
                url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
                destinationFile = destination,
            )
        }
        assertArrayEquals(existingBytes, destination.readBytes())
    }

    @Test
    fun `download stream failure removes temporary file`() {
        val destination = destinationFile("face.jpg")
        val tempFile = File(temporaryFolder.root, "${destination.name}.tmp")
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                failingResponseBody()
            }
        )

        assertThrows(IOException::class.java) {
            downloader.downloadToFile(
                url = "https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg",
                destinationFile = destination,
            )
        }
        assertFalse(destination.exists())
        assertFalse(tempFile.exists())
    }

    private fun fakeClient(
        responseCode: Int = 200,
        responseBodyProvider: () -> okhttp3.ResponseBody = {
            pngBytes().toResponseBody("image/png".toMediaType())
        },
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("OK")
                    .body(responseBodyProvider())
                    .build()
            })
            .build()
    }

    private fun pngBytes(): ByteArray {
        return Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC",
            Base64.DEFAULT,
        )
    }

    private fun destinationFile(name: String): File {
        return File(temporaryFolder.root, name)
    }

    private fun bytesOfSize(size: Int): ByteArray {
        return ByteArray(size) { index -> (index % 251).toByte() }
    }

    private fun failingResponseBody(): ResponseBody {
        return object : ResponseBody() {
            override fun contentType() = "image/jpeg".toMediaType()

            override fun contentLength() = -1L

            override fun source(): BufferedSource {
                return object : Source {
                    private var emitted = false

                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (!emitted) {
                            emitted = true
                            sink.writeUtf8("partial")
                            return "partial".length.toLong()
                        }
                        throw IOException("stream failed")
                    }

                    override fun timeout(): Timeout = Timeout.NONE

                    override fun close() = Unit
                }.buffer()
            }
        }
    }

    private companion object {
        const val PREVIOUS_PROTECTIVE_LIMIT_BYTES = 10 * 1024 * 1024
    }
}
