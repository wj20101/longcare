package com.ytone.longcare.network

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import okio.source
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OkioCompatibilityTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `legacy no argument binary signature retains base64 padding`() {
        // Libraries compiled before 3.18 call this JVM signature, not the new default argument API.
        val legacyBase64 = ByteString::class.java.getMethod("base64")
        mapOf("f" to "Zg==", "fo" to "Zm8=", "foo" to "Zm9v").forEach { (raw, encoded) ->
            assertEquals(encoded, legacyBase64.invoke(raw.encodeUtf8()))
        }
    }

    @Test
    fun `okhttp request body preserves utf8 bytes and content length`() {
        val payload = "{\"name\":\"测试对象\",\"description\":\"评估\"}".toByteArray()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals(payload.size.toLong(), body.contentLength())
        assertArrayEquals(payload, buffer.readByteArray())
    }

    @Test
    fun `response streams across buffer boundaries into a complete file`() {
        val payload = ByteArray(65_539) { (it % 251).toByte() }
        val target = temporaryFolder.newFile("download.apk")
        payload.toResponseBody().use { response ->
            target.sink().buffer().use { sink ->
                val source = response.source()
                val buffer = Buffer()
                while (true) {
                    val count = source.read(buffer, 8_192)
                    if (count == -1L) break
                    sink.write(buffer, count)
                }
            }
        }
        target.source().buffer().use { source ->
            assertArrayEquals(payload, source.readByteArray())
            assertTrue(source.exhausted())
        }
        assertEquals(payload.size.toLong(), target.length())
    }

    @Test
    fun `failed file write closes sink without leaking buffered data`() {
        val target = temporaryFolder.newFile("partial.apk")
        val sink = target.sink().buffer()
        assertThrows(IOException::class.java) {
            sink.use {
                it.writeUtf8("partial")
                throw IOException("simulated transfer failure")
            }
        }
        assertThrows(IllegalStateException::class.java) { sink.writeByte(1) }
        target.source().buffer().use { assertEquals("partial", it.readUtf8()) }
    }
}
