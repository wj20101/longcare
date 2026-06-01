package com.ytone.longcare.features.identification.data

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteFaceImageDownloaderTest {

    @Test
    fun `download accepts https cos image response`() {
        val imageBytes = jpegBytes()
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                imageBytes.toResponseBody("image/jpeg".toMediaType())
            }
        )

        val result = downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")

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
    fun `download rejects oversized image`() {
        val oversizedJpeg = jpegBytes() + ByteArray(512 * 1024) { 1 }
        val downloader = RemoteFaceImageDownloader(
            callFactory = fakeClient {
                oversizedJpeg.toResponseBody("image/jpeg".toMediaType())
            }
        )

        assertThrows(IOException::class.java) {
            downloader.download("https://longcare-bucket.cos.ap-beijing.myqcloud.com/face.jpg")
        }
    }

    private fun fakeClient(
        responseBodyProvider: () -> okhttp3.ResponseBody = {
            jpegBytes().toResponseBody("image/jpeg".toMediaType())
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

    private fun jpegBytes(): ByteArray {
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    }
}
