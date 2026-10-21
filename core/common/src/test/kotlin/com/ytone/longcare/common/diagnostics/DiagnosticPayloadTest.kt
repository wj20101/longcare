package com.ytone.longcare.common.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticPayloadTest {
    @Test
    fun `long failure details cannot displace the submitted location evidence`() {
        val sample = com.ytone.longcare.model.LocationResult(
            31.13812345678, 121.98765432109876, "network", accuracy = 18f,
            coordType = "GCJ02", locationType = 5, trustedLevel = 1,
            locationTime = 1_800_000_000_000, receivedAt = 1_800_000_000_100,
        )
        val extras = linkedMapOf<String, Any?>(
            "orderId" to 1234567890123L, "planId" to 123456789,
            "signInMode" to "END_ORDER", "stage" to "end_submit",
            "nfcDeviceIdLength" to 100, "nfcDeviceIdHash" to -123456789,
            "failureCode" to 4001, "failureMessage" to "超出范围".repeat(40),
        ) + locationDiagnosticExtras(sample, sample.receivedAt)
        val fields = DiagnosticPayload.create("nfc_workflow", "end_order_submit_failure", "ERROR", "2147483647",
            "NFC结束工单提交业务失败", IllegalStateException("\"".repeat(160)), extras)
        val expected = locationDiagnosticExtras(sample, sample.receivedAt)
        listOf("latitude", "longitude", "accuracy", "coordType", "locationTime", "sampleAgeMs", "provider",
            "locationType", "trustedLevel", "isMock", "isLastLocation", "amapErrorCode").forEach {
            assertEquals("Missing location evidence: $it", expected[it].toString(), fields[it])
        }
        assertEquals("4001", fields["failureCode"])
        assertTrue(DiagnosticPayload.encode(fields).length <= 950)
    }

    @Test
    fun `only NFC distance diagnostics retain exact numeric coordinates`() {
        val coordinates = mapOf("latitude" to 31.13812345678, "longitude" to 121.98765432109876, "token" to "secret")
        for ((category, event) in listOf("location" to "nfc_location_acquired", "nfc_workflow" to "end_order_submit_failure")) {
            val fields = DiagnosticPayload.create(category, event, "INFO", "123", "定位", null, coordinates)
            assertEquals("31.13812345678", fields["latitude"])
            assertEquals("121.98765432109876", fields["longitude"])
            assertFalse(fields.containsKey("token"))
        }
        val ordinary = DiagnosticPayload.create("location", "location_sample_recorded", "INFO", "123", "定位", null, coordinates)
        assertFalse(ordinary.containsKey("latitude"))
        assertFalse(ordinary.containsKey("longitude"))
        val invalid = DiagnosticPayload.create("location", "nfc_location_failed", "ERROR", "123", "定位", null,
            mapOf("latitude" to Double.NaN, "longitude" to "121.0 token=secret"))
        assertFalse(invalid.containsKey("latitude"))
        assertFalse(invalid.containsKey("longitude"))
    }

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

    @Test
    fun `optional error text cannot crowd out alarm timing evidence`() {
        val extras = linkedMapOf<String, Any?>(
            "failureMessage" to "optional".repeat(100),
            "orderId" to 1234567890123L, "outcome" to "playback_started",
            "plannedAt" to 1_800_000_000_000L, "scheduleMode" to "inexact_idle",
            "fallback" to "exact_permission_denied", "receiverDelayMs" to 180000L,
            "validationMs" to 50L, "serviceDispatchMs" to 40L, "playbackSetupMs" to 100L,
            "clockShiftMs" to 0L, "sleepMs" to 600000L,
        )
        val fields = DiagnosticPayload.create("countdown", "alarm_delivery", "INFO", "123",
            "闹钟播放时序", IllegalStateException("optional".repeat(100)), extras)
        extras.filterKeys { it != "failureMessage" }.forEach { (key, value) ->
            assertEquals("Missing timing evidence: $key", value.toString(), fields[key])
        }
        assertTrue(DiagnosticPayload.encode(fields).length <= 950)
    }

    @Test
    fun `checkout summary preserves correlation stage durations and submitted coordinates`() {
        val extras = linkedMapOf<String, Any?>(
            "failureMessage" to "超出范围".repeat(100),
            "orderId" to 1234567890123L, "planId" to 123456789, "signInMode" to "END_ORDER",
            "attemptId" to "cd47d834-d58a-45f8-9c0b-0f7bcf1fe301",
            "entry" to "confirmation", "outcome" to "submit_failed", "stage" to "submit",
            "durationMs" to 65000L, "checkMs" to 5000L, "locationMs" to 30000L,
            "submitMs" to 30000L, "failureCode" to 4001,
            "longitude" to 121.98765432109876, "latitude" to 31.13812345678,
            "coordType" to "GCJ02", "locationTime" to 1_800_000_000_000L,
            "accuracy" to 18f, "requestId" to "cd47d834-d58a-45f8-9c0b-0f7bcf1fe302",
        )
        val fields = DiagnosticPayload.create("nfc_workflow", "nfc_end_order_result", "INFO", "123",
            "签退流程诊断", null, extras)
        extras.filterKeys { it != "failureMessage" }.forEach { (key, value) ->
            assertEquals("Missing checkout evidence: $key", value.toString(), fields[key])
        }
        assertTrue(DiagnosticPayload.encode(fields).length <= 950)
    }

    @Test
    fun `full background health summary retains sleep network and failure counters`() {
        val extras = linkedMapOf<String, Any?>(
            "orderId" to 1234567890123L, "sessionId" to "cd47d834-d58a-45f8-9c0b-0f7bcf1fe301",
            "stage" to "summary", "reason" to "callback_silence", "failureEpisode" to 20L,
            "successGapMs" to 1_200_000L, "rawGapMs" to 120_000L, "pendingMs" to 29_000L,
            "errorCode" to 4001, "sdkErrorCode" to 4, "qualityReason" to "ACCURACY",
            "apiFailure" to "api_network", "apiErrorType" to "SocketTimeoutException",
            "maxTickGapMs" to 120_000L, "maxSleepGapMs" to 90_000L,
            "deviceIdle" to true, "screenOn" to false, "networkAvailable" to true,
            "networkValidated" to false, "batteryExempt" to false, "powerSave" to true,
            "clientId" to "cd47d834-d58a-45f8-9c0b-0f7bcf1fe302",
            "failureCount" to 30, "callbackCount" to 100, "rejectedCount" to 50,
        )
        val fields = DiagnosticPayload.create("location", "reporting_health", "INFO", "2147483647",
            "后台定位上报健康汇总", null, extras)
        extras.forEach { (key, value) -> assertEquals("Missing health evidence: $key", value.toString(), fields[key]) }
        assertTrue(DiagnosticPayload.encode(fields).length <= 950)
    }
}
