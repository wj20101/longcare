package com.ytone.longcare.features.identification.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteFaceImageDownloaderTest {

    @Test
    fun `download accepts https cos image response`() {
        val imageBytes = pngBytes()
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/png".toMediaType())
            }
        )

        val result = downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.png")

        assertArrayEquals(imageBytes, result)
    }

    @Test
    fun `download rejects non https url`() {
        val downloader = RemoteFaceImageDownloader(callFactory = fakeClient())

        assertThrows(IOException::class.java) {
            downloader.download("http://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    @Test
    fun `download rejects untrusted host`() {
        val downloader = RemoteFaceImageDownloader(callFactory = fakeClient())

        assertThrows(IOException::class.java) {
            downloader.download("https://example.com/face.jpg")
        }
    }

    @Test
    fun `download rejects unsupported content type`() {
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                "not an image".toResponseBody("text/plain".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    @Test
    fun `download rejects undecodable image bytes`() {
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                truncatedJpegHeaderBytes().toResponseBody("image/jpeg".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    @Test
    fun `download rejects corrupted image with valid header`() {
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                corruptedJpegWithReadableBoundsBytes().toResponseBody("image/jpeg".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    @Test
    fun `download accepts image larger than previous local limit`() {
        val imageBytes = largeJpegBytes()
        assertTrue(imageBytes.size > 512 * 1024)
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        val result = downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")

        assertArrayEquals(imageBytes, result)
    }

    @Test
    fun `download rejects image above protective limit`() {
        val imageBytes = veryLargeJpegBytes()
        assertTrue(imageBytes.size > PROTECTIVE_LIMIT_BYTES)
        FaceImageValidation.requireSupportedFaceImageBytes(imageBytes)
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    private fun fakeClient(
        responseBodyProvider: () -> okhttp3.ResponseBody = {
            pngBytes().toResponseBody("image/png".toMediaType())
        },
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
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

    private fun truncatedJpegHeaderBytes(): ByteArray {
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    }

    private fun corruptedJpegWithReadableBoundsBytes(): ByteArray {
        val validJpeg = smallJpegBytes()
        return validJpeg.copyOf((validJpeg.size / 2).coerceAtLeast(4))
    }

    private fun smallJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun largeJpegBytes(): ByteArray {
        return randomJpegBytes(width = 1024, height = 1024)
    }

    private fun veryLargeJpegBytes(): ByteArray {
        return randomJpegBytes(width = 3072, height = 3072).also { bytes ->
            check(bytes.size > PROTECTIVE_LIMIT_BYTES) {
                "Generated JPEG must exceed the protective download limit."
            }
        }
    }

    private fun randomJpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { index ->
            val value = index * 1103515245 + 12345
            Color.rgb(value and 0xFF, value ushr 8 and 0xFF, value ushr 16 and 0xFF)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
        bitmap.recycle()
        return output.toByteArray()
    }

    private companion object {
        const val PROTECTIVE_LIMIT_BYTES = 10 * 1024 * 1024
    }
}
