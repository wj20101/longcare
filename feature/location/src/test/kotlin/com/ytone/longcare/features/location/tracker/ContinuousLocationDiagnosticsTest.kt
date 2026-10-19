package com.ytone.longcare.features.location.tracker

import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationQualityReport
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.KLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContinuousLocationDiagnosticsTest {
    private data class Report(
        val event: String,
        val isError: Boolean,
        val extras: Map<String, Any?>,
        val userId: String,
        val reportToServer: Boolean,
    )

    private var now = 10_000L
    private val reports = mutableListOf<Report>()
    private lateinit var diagnostics: ContinuousLocationDiagnostics

    @Before fun setup() {
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "123"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } answers {
            reports += Report(arg(1), true, arg(4), arg(5), true)
        }
        every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } answers {
            reports += Report(arg(1), false, arg(3), arg(4), arg(5))
        }
        diagnostics = ContinuousLocationDiagnostics("request-1", 30_000L) { now }
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `same failure reports immediately then summarizes only on a callback after five minutes`() {
        val failed = location(AMapLocation.ERROR_CODE_FAILURE_CONNECTION)
        diagnostics.onLocation(failed, 30_000L)
        assertReport(reports.single(), "first", 1L, 0L, isError = true)
        now += 299_999L
        diagnostics.onLocation(failed, 3_000L)
        assertEquals(1, reports.size)
        now += 1L
        diagnostics.onLocation(failed, 3_000L)
        assertEquals(2, reports.size)
        assertReport(reports.last(), "summary", 3L, 300_000L, isError = true)
        assertEquals(3_000L, reports.last().extras["intervalMs"])
        assertEquals("4:3", reports.last().extras["errorCounts"])
        diagnostics.finish()
        assertEquals("An already summarized window needs no closing report", 2, reports.size)
    }

    @Test fun `intermittent success does not reset the remote reporting window`() {
        repeat(10) {
            diagnostics.onLocation(location(4), 30_000L)
            diagnostics.onLocation(location(0), 30_000L)
            diagnostics.onLocation(location(0), 30_000L)
            now += 30_000L
        }
        assertEquals("4-success-4 must not report every recovery and new failure", 1, reports.size)
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(2, reports.size)
        assertReport(reports.last(), "summary", 10L, 300_000L, isError = false)
        assertEquals(10L, reports.last().extras["recoveryCount"])
        assertEquals("amap_continuous_location_recovered", reports.last().event)
        diagnostics.finish()
        assertEquals(2, reports.size)
    }

    @Test fun `different error codes share the same bounded summary window`() {
        listOf(4, 13, 4, 12, 13).forEach {
            diagnostics.onLocation(location(it), 3_000L)
            now += 3_000L
        }
        assertEquals(1, reports.size)
        diagnostics.finish()
        assertReport(reports.last(), "finished", 5L, 15_000L, isError = false)
        assertEquals(13, reports.last().extras["errorCode"])
        assertEquals("4:2,13:2,12:1", reports.last().extras["errorCounts"])
        assertEquals(0L, reports.last().extras["recoveryCount"])
    }

    @Test fun `the next window starts at its report and not at intervening errors or recoveries`() {
        diagnostics.onLocation(location(4), 30_000L)
        now += 299_999L
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(1, reports.size)
        now += 1L
        diagnostics.onLocation(location(13), 30_000L)
        assertEquals(2, reports.size)
        assertReport(reports.last(), "summary", 2L, 300_000L, isError = true)
        now += 299_999L
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        assertEquals(2, reports.size)
        now += 1L
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(3, reports.size)
        assertReport(reports.last(), "summary", 3L, 600_000L, isError = false)
        assertEquals(3L, reports.last().extras["recoveryCount"])
    }

    @Test fun `window identity advances when recovery adds no new failure count`() {
        diagnostics.onLocation(location(4), 3_000L)
        now += 300_000L
        diagnostics.onLocation(location(4), 3_000L)
        now += 300_000L
        diagnostics.onLocation(location(0), 3_000L)
        diagnostics.onLocation(location(4), 3_000L)
        diagnostics.finish()
        assertEquals(listOf(1L, 2L, 3L, 4L), reports.map { it.extras["failureEpisode"] })
        assertEquals(listOf(1L, 2L, 2L, 3L), reports.map { it.extras["failureCount"] })
        assertTrue(reports.all { it.extras["requestId"] == "request-1" })
    }

    @Test fun `finish settles failure and recovery changes once then ignores late callbacks`() {
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertReport(reports.last(), "finished", 2L, 30_000L, isError = false)
        assertEquals(1L, reports.last().extras["recoveryCount"])
        assertEquals("amap_continuous_location_recovered", reports.last().event)
        diagnostics.finish()
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(12), 30_000L)
        assertEquals(2, reports.size)
    }

    @Test fun `empty successful and first-only sampling produce no extra closing report`() {
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.finish()
        assertTrue(reports.isEmpty())
        val other = ContinuousLocationDiagnostics("request-2", 30_000L) { now }
        other.onLocation(location(4), 30_000L)
        other.finish()
        assertEquals(1, reports.size)
    }

    @Test fun `success-only callbacks do not produce empty periodic summaries`() {
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(0), 30_000L)
        now += 300_000L
        diagnostics.onLocation(location(0), 30_000L)
        now += 300_000L
        repeat(20) { diagnostics.onLocation(location(0), 30_000L) }
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertEquals(1L, reports.last().extras["recoveryCount"])
    }

    @Test fun `error code histogram stays bounded and retains the latest code`() {
        (1..100).forEach { diagnostics.onLocation(location(it), 30_000L) }
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertEquals(100, reports.last().extras["errorCode"])
        assertEquals("1:1,2:1,3:1,4:1,other:96", reports.last().extras["errorCounts"])
        assertEquals(100L, reports.last().extras["failureCount"])
    }

    @Test fun `only allowlisted failure classification and scalar quality evidence leave SDK detail`() {
        val raw = location(4)
        every { raw.errorInfo } returns "java.net.UnknownHostException: apiKey=secret-key-123"
        every { raw.locationDetail } returns "#0402 SSID=private-home latitude=31.123456 longitude=121.987654"
        every { raw.locationQualityReport } returns quality()
        diagnostics.onLocation(raw, 30_000L)
        val fields = reports.single().extras
        assertEquals("UnknownHostException", fields["networkFailureKind"])
        assertEquals("0402", fields["sdkDetailCode"])
        assertEquals("WIFI", fields["networkType"])
        assertEquals(20_001L, fields["netUseTimeMs"])
        assertEquals(true, fields["wifiAble"])
        assertEquals(6, fields["satellites"])
        val serialized = fields.toString()
        listOf("secret-key-123", "private-home", "31.123456", "121.987654", "errorInfo", "locationDetail")
            .forEach { assertFalse("Raw SDK detail leaked: $it", serialized.contains(it)) }
    }

    @Test fun `unknown network type and nonallowlisted detail are omitted`() {
        val raw = location(4)
        every { raw.errorInfo } returns "sensitive apiKey=private"
        every { raw.locationDetail } returns "#12345 custom-SocketTimeoutExceptionSecret"
        every { raw.locationQualityReport } returns mockk(relaxed = true) {
            every { networkType } returns "wifi-SSID-private"
        }
        diagnostics.onLocation(raw, 30_000L)
        val fields = reports.single().extras
        assertFalse(fields.containsKey("networkType"))
        assertFalse(fields.containsKey("networkFailureKind"))
        assertFalse(fields.containsKey("sdkDetailCode"))
    }

    @Test fun `diagnostic getters failing never prevent failure reporting or recovery`() {
        val raw = location(4)
        every { raw.errorInfo } throws IllegalStateException("broken SDK diagnostic getter")
        every { raw.locationDetail } throws IllegalStateException("broken SDK diagnostic getter")
        every { raw.locationQualityReport } returns mockk {
            every { networkType } throws IllegalStateException("broken quality getter")
            every { netUseTime } throws IllegalStateException("broken quality getter")
            every { isWifiAble } throws IllegalStateException("broken quality getter")
            every { getGPSStatus() } throws IllegalStateException("broken quality getter")
            every { getGPSSatellites() } throws IllegalStateException("broken quality getter")
        }
        diagnostics.onLocation(raw, 30_000L)
        assertEquals(1, reports.size)
        diagnostics.onLocation(location(0), 30_000L)
        val unreadable = location(4)
        every { unreadable.errorCode } throws IllegalStateException("broken code getter")
        diagnostics.onLocation(unreadable, 30_000L)
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertEquals("amap_continuous_location_recovered", reports.last().event)
    }

    @Test fun `all reports retain the identity captured at acquisition creation`() {
        diagnostics.onLocation(location(4), 30_000L)
        every { DiagnosticEventTracker.currentUserId() } returns "456"
        now += 300_000L
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.finish()
        assertEquals(3, reports.size)
        assertTrue(reports.all { it.userId == "123" })
        val next = ContinuousLocationDiagnostics("request-next", 30_000L) { now }
        next.onLocation(location(4), 30_000L)
        assertEquals("456", reports.last().userId)
    }

    @Test fun `identity or clock source failure never escapes SDK callbacks or cleanup`() {
        every { DiagnosticEventTracker.currentUserId() } throws IllegalStateException("user source unavailable")
        var clockBroken = false
        val actual = ContinuousLocationDiagnostics("request-broken", 30_000L,
            elapsedRealtime = { if (clockBroken) error("clock unavailable") else now },
        )
        actual.onLocation(location(4), 30_000L)
        assertEquals("0", reports.single().userId)
        actual.onLocation(location(4), 30_000L)
        clockBroken = true
        actual.onLocation(location(4), 30_000L)
        actual.finish()
        assertEquals(2, reports.size)
    }

    @Test fun `reporting exceptions never escape callbacks or cleanup and do not cause a retry storm`() {
        var attempts = 0
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } answers {
            attempts += 1
            error("diagnostic delivery unavailable")
        }
        every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } answers {
            attempts += 1
            error("diagnostic delivery unavailable")
        }
        repeat(10) {
            diagnostics.onLocation(location(4), 30_000L)
            diagnostics.onLocation(location(0), 30_000L)
            diagnostics.onLocation(location(13), 30_000L)
        }
        diagnostics.finish()
        diagnostics.finish()
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(2, attempts)
    }

    @Test fun `real formatting preserves aggregate and latest cause within the remote payload boundary`() {
        val delivered = actualReports()
        val actual = ContinuousLocationDiagnostics("request-1", 30_000L) { now }
        val raw = location(4)
        every { raw.locationDetail } returns "java.net.SocketTimeoutException #0402 apiKey=secret-value SSID=private-home"
        actual.onLocation(raw, 30_000L)
        now += 30_000L
        actual.onLocation(raw, 30_000L)
        actual.onLocation(location(0), 30_000L)
        actual.finish()
        assertEquals(2, delivered.size)
        val fields = delivered.last().fields
        assertEquals("INFO", fields["level"])
        assertEquals("finished", fields["stage"])
        assertEquals("2", fields["failureCount"])
        assertEquals("1", fields["recoveryCount"])
        assertEquals("4:2", fields["errorCounts"])
        assertEquals("30000", fields["observationDurationMs"])
        assertEquals("SocketTimeoutException", fields["networkFailureKind"])
        assertEquals("0402", fields["sdkDetailCode"])
        assertEquals("123", fields["userId"])
        assertTrue(requireNotNull(delivered.last().message).length <= 950)
        assertFalse(fields.toString().contains("secret-value"))
        assertFalse(fields.toString().contains("private-home"))
    }

    @Test fun `runtime context is refreshed only for reports and does not block recovery`() {
        var reads = 0
        var validated = false
        val contextual = ContinuousLocationDiagnostics("request-context", 30_000L,
            runtimeSnapshot = {
                reads += 1
                mapOf("networkValidated" to validated, "deviceIdle" to !validated)
            },
            elapsedRealtime = { now },
        )
        contextual.onLocation(location(4), 30_000L)
        assertEquals(false, reports.single().extras["networkValidated"])
        repeat(8) { contextual.onLocation(location(4), 30_000L) }
        contextual.onLocation(location(0), 30_000L)
        assertEquals("Repeated or recovery callbacks must not read Android services", 1, reads)
        validated = true
        now += 300_000L
        contextual.onLocation(location(0), 30_000L)
        assertEquals(true, reports.last().extras["networkValidated"])
        assertEquals(false, reports.last().extras["deviceIdle"])
        contextual.finish()
        assertEquals(2, reads)
        assertEquals("summary", reports.last().extras["stage"])

        val broken = ContinuousLocationDiagnostics("request-broken", 30_000L,
            runtimeSnapshot = { error("Android state temporarily unavailable") },
            elapsedRealtime = { now },
        )
        broken.onLocation(location(4), 30_000L)
        broken.onLocation(location(0), 30_000L)
        broken.finish()
        assertEquals("amap_continuous_location_recovered", reports.last().event)
    }

    @Test fun `short intermittent failures retain the latest SDK cause and error code`() {
        val first = location(4).also {
            every { it.locationDetail } returns "java.net.SocketTimeoutException #0402"
        }
        val last = location(13).also {
            every { it.locationDetail } returns "java.net.UnknownHostException #0401 SSID=private"
        }
        diagnostics.onLocation(first, 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(last, 30_000L)
        assertEquals(1, reports.size)
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertEquals("amap_continuous_location_recovered", reports.last().event)
        assertEquals(13, reports.last().extras["errorCode"])
        assertEquals(2L, reports.last().extras["recoveryCount"])
        assertEquals("UnknownHostException", reports.last().extras["networkFailureKind"])
        assertEquals("0401", reports.last().extras["sdkDetailCode"])
        assertFalse(reports.last().extras.toString().contains("private"))
    }

    @Test fun `actual formatting prioritizes aggregate reason and runtime identity when context is full`() {
        val delivered = actualReports("2147483647")
        val contextual = ContinuousLocationDiagnostics("12345678-1234-1234-1234-123456789012", 30_000L,
            runtimeSnapshot = { mapOf(
                "clientPurpose" to "service", "orderId" to Long.MAX_VALUE,
                "androidApi" to 35, "appVersionCode" to 74L, "amapSdkVersion" to "11.3.101",
                "networkAvailable" to true, "networkValidated" to false, "networkTransport" to "CELLULAR",
                "deviceIdle" to true, "screenOn" to false, "powerSave" to true, "batteryExempt" to false,
            ) }, elapsedRealtime = { now },
        )
        val raw = location(4)
        every { raw.locationDetail } returns "javax.net.ssl.SSLPeerUnverifiedException #0402"
        every { raw.locationQualityReport } returns quality()
        contextual.onLocation(raw, 30_000L)
        listOf(13, 12, 11, 10, 9).forEach { contextual.onLocation(location(it), 30_000L) }
        contextual.onLocation(raw, 30_000L)
        contextual.onLocation(location(0), 30_000L)
        contextual.finish()
        val fields = delivered.last().fields
        listOf("requestId", "failureEpisode", "errorCode", "failureCount", "recoveryCount", "errorCounts",
            "observationDurationMs", "stage", "intervalMs", "httpTimeoutMs", "networkFailureKind", "sdkDetailCode",
            "clientPurpose", "orderId", "androidApi", "appVersionCode", "amapSdkVersion")
            .forEach { assertTrue("Required diagnostic field was truncated: $it", fields.containsKey(it)) }
        assertEquals("4:2,13:1,12:1,11:1,other:2", fields["errorCounts"])
        assertTrue(delivered.all { requireNotNull(it.message).length <= 950 })
        assertTrue(delivered.all { it.fields.size <= 30 })
    }

    private fun actualReports(userId: String = "123"): MutableList<DiagnosticException> {
        unmockkObject(DiagnosticEventTracker)
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns userId
        val delivered = mutableListOf<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(any()) } answers { delivered += firstArg<DiagnosticException>() }
        return delivered
    }

    private fun assertReport(report: Report, stage: String, count: Long, observationDuration: Long, isError: Boolean) {
        assertEquals(stage, report.extras["stage"])
        assertEquals(count, report.extras["failureCount"])
        assertEquals(observationDuration, report.extras["observationDurationMs"])
        assertEquals("request-1", report.extras["requestId"])
        assertEquals(30_000L, report.extras["httpTimeoutMs"])
        assertEquals(isError, report.isError)
        assertTrue(report.reportToServer)
        assertFalse(report.extras.containsKey("locationTime"))
    }

    private fun quality(): AMapLocationQualityReport = mockk {
        every { networkType } returns "WiFi"
        every { netUseTime } returns 20_001L
        every { isWifiAble } returns true
        every { getGPSStatus() } returns AMapLocationQualityReport.GPS_STATUS_OK
        every { getGPSSatellites() } returns 6
    }

    private fun location(code: Int): AMapLocation = mockk {
        every { errorCode } returns code
        every { errorInfo } returns ""
        every { locationDetail } returns ""
        every { locationQualityReport } returns null
    }
}
