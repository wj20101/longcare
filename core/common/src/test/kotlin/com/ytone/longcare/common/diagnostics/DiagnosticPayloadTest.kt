package com.ytone.longcare.common.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticPayloadTest {
    @Test
    fun `dynamic identity and details do not change grouping frames`() {
        val original = IllegalStateException("token=secret").apply {
            stackTrace = arrayOf(StackTraceElement("Camera", "capture", "Camera.kt", 12))
        }
        fun event(userId: String, event: String = "capture_failed") = DiagnosticException(
            DiagnosticPayload.create("camera", event, "ERROR", userId, "失败", original, emptyMap()), original,
        )
        val first = event("123")
        val second = event("456")
        assertArrayEquals(first.stackTrace, second.stackTrace)
        assertNotEquals(first.stackTrace.toList(), event("123", "decode_failed").stackTrace.toList())
        assertNull(first.cause)
        assertEquals(original.stackTrace[0], first.stackTrace[1])
        assertEquals("123", DiagnosticPayload.decode(first.message!!)["userId"])
        assertFalse(first.message!!.contains("secret"))
    }

    @Test
    fun `payload remains valid below SDK truncation and cannot override identity`() {
        val extras = (1..50).associate { "key$it" to "数据".repeat(200) } + mapOf(
            "userId" to "other", "category" to "other", "token" to "secret",
        )
        val fields = DiagnosticPayload.create("location", "upload_failed", "ERROR", "123", "失败", null, extras)
        val encoded = DiagnosticPayload.encode(fields)
        assertTrue(encoded.length <= 950)
        assertEquals(fields, DiagnosticPayload.decode(encoded))
        assertEquals("123", fields["userId"])
        assertEquals("location", fields["category"])
        assertFalse(fields.containsKey("token"))
    }

    @Test
    fun `all diagnostic text is sanitized and arbitrary objects are excluded`() {
        val original = IllegalStateException("password=\"secret with spaces\"").apply {
            initCause(IllegalArgumentException("token=hidden"))
            addSuppressed(IllegalStateException("https://host/private?token=abc"))
        }
        val fields = DiagnosticPayload.create("location", "failed", "ERROR", "123", "请求失败", original,
            mapOf("latitude" to 31.2, "longitude" to 120.2, "face_img" to "image-data", "mobile" to "13812345678",
                "object" to object { override fun toString(): String = error("must not serialize") },
                "reason" to "https://host/secret?token=abc", "orderId" to 123L))
        val text = DiagnosticPayload.encode(fields)
        listOf("secret", "hidden", "image-data", "13812345678", "https://", "latitude", "longitude").forEach {
            assertFalse("Unexpected sensitive content: $it", text.contains(it))
        }
        assertEquals("123", fields["orderId"])
        assertTrue(fields.containsKey("relatedError0"))
    }
}
