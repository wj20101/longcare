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
        assertEquals(1, reports.size)
        assertReport(reports.single(), "first", 1L, 0L, isError = true)
        now += 299_999L
        diagnostics.onLocation(failed, 3_000L)
        assertEquals(1, reports.size)
        now += 1L
        diagnostics.onLocation(failed, 3_000L)
        assertEquals(2, reports.size)
        assertReport(reports.last(), "summary", 3L, 300_000L, isError = true)
        assertEquals(3_000L, reports.last().extras["intervalMs"])
        diagnostics.finish()
        assertEquals("An already summarized failure needs no closing report", 2, reports.size)
    }

    @Test fun `success reports recovery once and a new failure starts a fresh episode`() {
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(2, reports.size)
        assertReport(reports.last(), "recovered", 2L, 60_000L, isError = false)
        assertEquals("amap_continuous_location_recovered", reports.last().event)
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        assertEquals(3, reports.size)
        assertReport(reports.last(), "first", 1L, 0L, isError = true)
        diagnostics.finish()
        assertEquals(3, reports.size)
    }

    @Test fun `failure episode identity advances even when callbacks share the same clock time`() {
        diagnostics.onLocation(location(4), 3_000L)
        diagnostics.onLocation(location(0), 3_000L)
        diagnostics.onLocation(location(4), 3_000L)
        diagnostics.onLocation(location(12), 3_000L)
        diagnostics.onLocation(location(4), 3_000L)
        assertEquals(listOf(1L, 1L, 2L, 3L, 4L), reports.map { it.extras["failureEpisode"] })
        assertTrue(reports.all { it.extras["requestId"] == "request-1" })
        assertTrue(reports.all { it.extras["failureCount"] == 1L })
        diagnostics.finish()
        assertEquals(5, reports.size)
    }

    @Test fun `different error settles unreported duplicates then reports the new error immediately`() {
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(12), 30_000L)
        assertEquals(3, reports.size)
        assertReport(reports[1], "error_changed", 2L, 60_000L, isError = false)
        assertEquals(4, reports[1].extras["errorCode"])
        assertReport(reports[2], "first", 1L, 0L, isError = true)
        assertEquals(12, reports[2].extras["errorCode"])
    }

    @Test fun `finish only settles unreported repeats once and ignores all late callbacks`() {
        diagnostics.onLocation(location(4), 30_000L)
        now += 30_000L
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.finish()
        assertEquals(2, reports.size)
        assertReport(reports.last(), "finished", 2L, 30_000L, isError = false)
        diagnostics.finish()
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(12), 30_000L)
        assertEquals(2, reports.size)
    }

    @Test fun `empty or first-only sampling produces no extra closing report`() {
        diagnostics.finish()
        assertTrue(reports.isEmpty())
        val other = ContinuousLocationDiagnostics("request-2", 30_000L) { now }
        other.onLocation(location(4), 30_000L)
        other.finish()
        assertEquals(1, reports.size)
    }

    @Test fun `only allowlisted failure classification and scalar quality evidence leave SDK detail`() {
        val raw = location(4)
        every { raw.errorInfo } returns "java.net.UnknownHostException: apiKey=secret-key-123"
        every { raw.locationDetail } returns "#0402 SSID=private-home latitude=31.123456 longitude=121.987654"
        val quality = mockk<AMapLocationQualityReport> {
            every { networkType } returns "WiFi"
            every { netUseTime } returns 20_001L
            every { isWifiAble } returns true
            every { getGPSStatus() } returns AMapLocationQualityReport.GPS_STATUS_OK
            every { getGPSSatellites() } returns 6
        }
        every { raw.locationQualityReport } returns quality
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
        assertEquals(2, reports.size)
        val unreadable = location(4)
        every { unreadable.errorCode } throws IllegalStateException("broken code getter")
        diagnostics.onLocation(unreadable, 30_000L)
        assertEquals(2, reports.size)
    }

    @Test fun `all episode reports retain the identity captured at acquisition creation`() {
        diagnostics.onLocation(location(4), 30_000L)
        every { DiagnosticEventTracker.currentUserId() } returns "456"
        now += 300_000L
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(0), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.finish()
        assertEquals(5, reports.size)
        assertTrue(reports.all { it.userId == "123" })
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
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.onLocation(location(4), 30_000L)
        diagnostics.finish()
        diagnostics.finish()
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(2, attempts)
    }

    @Test fun `real diagnostic formatting preserves aggregate evidence within the remote payload boundary`() {
        unmockkObject(DiagnosticEventTracker)
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "123"
        val delivered = mutableListOf<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(any()) } answers { delivered += firstArg<DiagnosticException>() }
        val actual = ContinuousLocationDiagnostics("request-1", 30_000L) { now }
        val raw = location(4)
        every { raw.locationDetail } returns "java.net.SocketTimeoutException #0402 apiKey=secret-value SSID=private-home"
        actual.onLocation(raw, 30_000L)
        now += 30_000L
        actual.onLocation(raw, 30_000L)
        actual.finish()
        assertEquals(2, delivered.size)
        val fields = delivered.last().fields
        assertEquals("INFO", fields["level"])
        assertEquals("finished", fields["stage"])
        assertEquals("2", fields["failureCount"])
        assertEquals("30000", fields["failureDurationMs"])
        assertEquals("SocketTimeoutException", fields["networkFailureKind"])
        assertEquals("0402", fields["sdkDetailCode"])
        assertEquals("123", fields["userId"])
        assertTrue(requireNotNull(delivered.last().message).length <= 950)
        assertFalse(fields.toString().contains("secret-value"))
        assertFalse(fields.toString().contains("private-home"))
    }

    private fun assertReport(report: Report, stage: String, count: Long, duration: Long, isError: Boolean) {
        assertEquals(stage, report.extras["stage"])
        assertEquals(count, report.extras["failureCount"])
        assertEquals(duration, report.extras["failureDurationMs"])
        assertEquals("request-1", report.extras["requestId"])
        assertEquals(30_000L, report.extras["httpTimeoutMs"])
        assertEquals(isError, report.isError)
        assertTrue(report.reportToServer)
        assertFalse(report.extras.containsKey("locationTime"))
    }

    @Test fun `runtime context is refreshed only for reports and does not block SDK recovery`() {
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
        assertEquals("Repeated callbacks must not read Android services", 1, reads)
        validated = true
        now += 300_000L
        contextual.onLocation(location(4), 30_000L)
        assertEquals(true, reports.last().extras["networkValidated"])
        assertEquals(false, reports.last().extras["deviceIdle"])
        contextual.onLocation(location(0), 30_000L)
        contextual.onLocation(location(0), 30_000L)
        contextual.finish()
        assertEquals(3, reads)
        assertEquals("recovered", reports.last().extras["stage"])

        val broken = ContinuousLocationDiagnostics("request-broken", 30_000L,
            runtimeSnapshot = { error("Android state temporarily unavailable") },
            elapsedRealtime = { now },
        )
        broken.onLocation(location(4), 30_000L)
        broken.onLocation(location(0), 30_000L)
        assertEquals("recovered", reports.last().extras["stage"])
    }

    @Test fun `short failure episodes retain the last SDK cause without reporting each callback`() {
        val first = location(4).also {
            every { it.locationDetail } returns "java.net.SocketTimeoutException #0402"
        }
        val last = location(4).also {
            every { it.locationDetail } returns "java.net.UnknownHostException #0401 SSID=private"
        }
        diagnostics.onLocation(first, 30_000L)
        now += 30_000L
        diagnostics.onLocation(last, 30_000L)
        assertEquals(1, reports.size)
        diagnostics.onLocation(location(0), 30_000L)
        assertEquals(2, reports.size)
        assertEquals("recovered", reports.last().extras["stage"])
        assertEquals("UnknownHostException", reports.last().extras["networkFailureKind"])
        assertEquals("0401", reports.last().extras["sdkDetailCode"])
        assertFalse(reports.last().extras.toString().contains("private"))
    }

    @Test fun `runtime and SDK context fit alongside failure identity in actual remote formatting`() {
        unmockkObject(DiagnosticEventTracker)
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "2147483647"
        val delivered = mutableListOf<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(any()) } answers { delivered += firstArg<DiagnosticException>() }
        val contextual = ContinuousLocationDiagnostics("12345678-1234-1234-1234-123456789012", 30_000L,
            runtimeSnapshot = { mapOf(
                "androidApi" to 35, "appVersionCode" to 72L, "amapSdkVersion" to "11.2.100",
                "networkAvailable" to true, "networkValidated" to false, "networkTransport" to "CELLULAR",
                "deviceIdle" to true, "screenOn" to false, "powerSave" to true, "batteryExempt" to false,
            ) }, elapsedRealtime = { now },
        )
        val raw = location(4)
        every { raw.locationDetail } returns "javax.net.ssl.SSLPeerUnverifiedException #0402"
        every { raw.locationQualityReport } returns mockk {
            every { networkType } returns "4G"
            every { netUseTime } returns 30_000L
            every { isWifiAble } returns false
            every { getGPSStatus() } returns AMapLocationQualityReport.GPS_STATUS_OFF
            every { getGPSSatellites() } returns 0
        }
        contextual.onLocation(raw, 30_000L)
        val fields = delivered.single().fields
        listOf("requestId", "failureEpisode", "errorCode", "failureCount", "failureDurationMs", "stage", "intervalMs", "httpTimeoutMs",
            "androidApi", "appVersionCode", "amapSdkVersion", "networkAvailable", "networkValidated", "networkTransport",
            "deviceIdle", "screenOn", "powerSave", "batteryExempt", "networkFailureKind", "sdkDetailCode", "networkType",
            "netUseTimeMs", "wifiAble", "gpsStatus", "satellites")
            .forEach { assertTrue("Diagnostic field was truncated: $it", fields.containsKey(it)) }
        assertTrue(requireNotNull(delivered.single().message).length <= 950)
    }

    private fun location(code: Int): AMapLocation = mockk {
        every { errorCode } returns code
        every { errorInfo } returns ""
        every { locationDetail } returns ""
        every { locationQualityReport } returns null
    }
}
