package com.ytone.longcare.features.location.service

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocationReportingDiagnosticsTest {
    private data class Report(val event: LocationEventTracker.EventType, val fields: Map<String, Any?>, val user: String)
    private val reports = mutableListOf<Report>()
    private var elapsed = 1_000L
    private var uptime = 1_000L
    private var snapshotReads = 0
    private lateinit var diagnostics: LocationReportingDiagnostics

    @Before fun setup() {
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackEvent(any(), any(), any(), any()) } answers {
            reports += Report(firstArg(), secondArg(), arg(3))
        }
        every { LocationEventTracker.trackError(any(), any(), any(), any()) } answers {
            reports += Report(firstArg(), arg(2), arg(3))
        }
        diagnostics = LocationReportingDiagnostics(17, "service-run", "3", { elapsed }, { uptime }, {
            snapshotReads++
            mapOf("runtime" to "idle=1,screen=0,net=1,valid=1,exempt=0,save=0")
        })
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `real remote payload preserves source counters and SDK boundary with a network exception`() {
        unmockkObject(LocationEventTracker)
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "2147483647"
        val delivered = mutableListOf<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(any()) } answers { delivered += firstArg<DiagnosticException>() }
        val runtime = mapOf(
            "clientId" to "cd47d834-d58a-45f8-9c0b-0f7bcf1fe302",
            "runtime" to "idle=?/?,screen=?,net=?,validated=?,exempt=?,save=?,bg=?,ds=?,lps=?",
            "sdkState" to "count=999999999+,null=999999999+,ageMs=999999999+,intervalMs=30000,visible=false,gS=999999+/999999+",
        )
        val required = setOf("orderId", "sessionId", "stage", "reason", "reportSequence", "successGapMs",
            "maxSuccessGapMs", "observedMs", "time", "accuracy",
            "locationType", "sdkErrorCode", "qualityReason", "apiFailure", "maxTickGapMs", "maxSleepGapMs",
            "counts", "clientId", "runtime", "sdkState")
        // The production adapter wraps failures in this longer class name; it must not crowd out evidence.
        val error = mockk<ApiRequestException>(relaxed = true)
        every { error.stackTrace } returns emptyArray()
        every { error.message } returns "private information must not displace evidence".repeat(20)
        assertEquals(ApiRequestException::class.java, error.javaClass)
        val samples = listOf(
            sample().copy(sourceElapsedRealtime = 1_000L) to "none",
            sample().copy(sourceElapsedRealtime = 1_001_000L) to "SOURCE_AFTER_RECEIPT",
            sample().copy(receivedElapsedRealtime = -1L, sourceElapsedRealtime = 1_000L) to "RECEIPT_ELAPSED_INVALID",
            sample().copy(receivedElapsedRealtime = 1_201_001L, sourceElapsedRealtime = 1_000L) to "ELAPSED_BEFORE_RECEIPT",
        )
        for ((location, issue) in samples) {
            elapsed = 1_000L
            val actual = LocationReportingDiagnostics(1234567890123L, "cd47d834-d58a-45f8-9c0b-0f7bcf1fe301",
                "2147483647", { elapsed }, { uptime }, { runtime })
            actual.onAcquisition(LocationAcquisition.Success(location))
            actual.onCandidate(if (issue == "none") "before_resume" else "TIME")
            elapsed += 1_200_000
            actual.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR, null, error)
            val report = delivered.last()
            required.forEach { assertNotNull("$issue missing diagnostic field $it: ${report.message}", report.fields[it]) }
            assertTrue(report.fields.getValue("time").contains("issue=$issue,"))
            assertEquals(runtime["sdkState"], report.fields["sdkState"])
            assertEquals("api_network,code=?", report.fields["apiFailure"])
            assertEquals(ApiRequestException::class.java.name, report.fields["errorType"])
            assertTrue(requireNotNull(report.message).length <= 950)
            assertTrue(report.fields.size <= 30)
            assertFalse(report.fields.containsKey("latitude"))
            assertFalse(report.fields.containsKey("longitude"))
        }
        assertEquals(samples.size, delivered.size)
    }

    @Test fun `missing callbacks and delayed health tick report once during idle without claiming a cause`() {
        elapsed += 59_999
        diagnostics.onHealthTick(null)
        assertTrue(reports.isEmpty())
        elapsed += 120_001
        uptime += 1
        diagnostics.onHealthTick(null)
        val fields = reports.single().fields
        assertEquals("no_callback", fields["reason"])
        assertEquals(180_000L, fields["successGapMs"])
        assertEquals(120_001L, fields["maxTickGapMs"])
        assertEquals(120_000L, fields["maxSleepGapMs"])
        assertEquals("idle=1,screen=0,net=1,valid=1,exempt=0,save=0", fields["runtime"])
        repeat(9) { elapsed += 30_000; diagnostics.onHealthTick(null) }
        assertEquals(1, reports.size)
        assertEquals(1, snapshotReads)
        elapsed += 30_000
        diagnostics.onHealthTick(null)
        assertEquals("summary", reports.last().fields["stage"])
    }

    @Test fun `SDK error quality rejection and API pending have different evidence`() {
        elapsed += 60_000
        diagnostics.onAcquisition(LocationAcquisition.Failure(LocationFailure.NETWORK, sample().copy(errorCode = 4)))
        diagnostics.onHealthTick(null)
        assertEquals("sdk_error", reports.last().fields["reason"])
        assertEquals(4, reports.last().fields["sdkErrorCode"])
        diagnostics.onUploadSuccess(sample(), 10)
        elapsed += 300_000
        diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
        diagnostics.onCandidate("ACCURACY")
        diagnostics.onHealthTick(null)
        assertEquals("sample_rejected", reports.last().fields["reason"])
        assertEquals(1L, count(reports.last().fields, "rej"))
        diagnostics.onUploadSuccess(sample(), 10)
        elapsed += 300_000
        diagnostics.onUploadStarted()
        elapsed += 5_000
        diagnostics.onHealthTick(null)
        assertEquals("api_pending", reports.last().fields["reason"])
        assertEquals(5_000L, reports.last().fields["requestMs"])
    }

    @Test fun `API failure episode aggregates mixed errors and recovery with original identity`() {
        diagnostics.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR, 400, null)
        repeat(9) {
            elapsed += 30_000
            diagnostics.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR, null, IllegalStateException("private text"))
        }
        assertEquals(1, reports.size)
        elapsed += 30_000
        diagnostics.onHealthTick(null)
        assertEquals(10L, count(reports.last().fields, "fail"))
        assertEquals("api_network,code=?,type=IllegalStateException", reports.last().fields["apiFailure"])
        diagnostics.onUploadSuccess(sample(), 25)
        val recovered = reports.last()
        assertEquals("first_success", recovered.fields["stage"])
        assertEquals("3", recovered.user)
        assertEquals(25L, recovered.fields["requestMs"])
        assertEquals("upload_success", recovered.fields["reason"])
        diagnostics.finish()
        assertEquals(4, reports.size)
    }

    @Test fun `paused state and synthetic marker do not masquerade as an SDK callback`() {
        diagnostics.onAcquisition(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED))
        elapsed += 60_000
        diagnostics.onHealthTick("location_disabled")
        assertEquals("location_disabled", reports.single().fields["reason"])
        assertEquals(0L, count(reports.single().fields, "cb"))
        assertNull(reports.single().fields["rawGapMs"])
    }

    @Test fun `dispatch revalidation does not inflate sample rejection count`() {
        diagnostics.onCandidate(null)
        diagnostics.onRevalidationRejected("STALE")
        elapsed += 60_000
        diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
        diagnostics.onHealthTick(null)
        assertEquals("STALE", reports.single().fields["qualityReason"])
        assertEquals(0L, count(reports.single().fields, "rej"))
    }

    @Test fun `reporting failure never escapes or causes immediate repeats and finish is idempotent`() {
        every { LocationEventTracker.trackEvent(any(), any(), any(), any()) } throws IllegalStateException("unavailable")
        elapsed += 60_000
        diagnostics.onHealthTick(null)
        assertEquals(1, snapshotReads)
        repeat(5) { diagnostics.onHealthTick(null) }
        assertEquals(1, snapshotReads)
        diagnostics.finish()
        diagnostics.finish()
        assertEquals(2, snapshotReads)
    }

    @Test fun `suppressed long sleep evidence survives normal ticks until the next summary`() {
        diagnostics.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR, null, IllegalStateException())
        elapsed += 120_000
        diagnostics.onHealthTick(null)
        repeat(6) {
            elapsed += 30_000
            uptime += 30_000
            diagnostics.onHealthTick(null)
        }
        assertEquals(2, reports.size)
        assertEquals(120_000L, reports.last().fields["maxTickGapMs"])
        assertEquals(120_000L, reports.last().fields["maxSleepGapMs"])
    }

    @Test fun `replayed samples retain original callback age`() {
        diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
        elapsed += 90_000
        diagnostics.onAcquisition(LocationAcquisition.Success(sample().copy(receivedElapsedRealtime = 1_000L)))
        diagnostics.onHealthTick(null)
        assertEquals("callback_silence", reports.single().fields["reason"])
        assertTrue(reports.single().fields["time"].toString().contains("elapsed=90000"))
    }

    @Test fun `terminal permission or privacy loss reports once even without an open gap`() {
        diagnostics.finish("permission_revoked")
        diagnostics.finish("collector_interrupted")
        assertEquals(1, reports.size)
        assertEquals("finished", reports.single().fields["stage"])
        assertEquals("permission_revoked", reports.single().fields["reason"])
        val privacy = LocationReportingDiagnostics(17, "other-run", "3", { elapsed }, { uptime })
        privacy.finish("privacy_revoked")
        assertEquals("privacy_revoked", reports.last().fields["reason"])
    }

    @Test fun `healthy sampling reports bounded five minute and final observations including tail`() {
        diagnostics.onUploadSuccess(sample(), 10)
        repeat(10) {
            elapsed += 30_000
            uptime += 30_000
            diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
            diagnostics.onCandidate(null)
            diagnostics.onUploadStarted()
            diagnostics.onUploadSuccess(sample(), 10)
            diagnostics.onHealthTick(null)
        }
        assertEquals(2, reports.size)
        assertEquals("summary", reports.last().fields["stage"])
        assertEquals("healthy", reports.last().fields["reason"])
        assertEquals(300_000L, reports.last().fields["observedMs"])
        assertEquals(30_000L, reports.last().fields["maxSuccessGapMs"])
        assertEquals(11L, count(reports.last().fields, "ok"))
        elapsed += 20_000
        diagnostics.finish()
        assertEquals(3, reports.size)
        assertEquals("finished", reports.last().fields["stage"])
        assertEquals(0L, reports.last().fields["maxSuccessGapMs"])
        assertEquals(20_000L, reports.last().fields["successGapMs"])
        assertEquals(3L, reports.last().fields["reportSequence"])
    }

    @Test fun `source clock evidence distinguishes reused source from fresh callback time`() {
        diagnostics.onAcquisition(LocationAcquisition.Success(sample().copy(sourceElapsedRealtime = 500L)))
        elapsed += 30_000
        diagnostics.onAcquisition(LocationAcquisition.Success(sample().copy(sourceElapsedRealtime = 500L)))
        diagnostics.onCandidate("before_resume")
        elapsed += 30_000
        diagnostics.onHealthTick(null)
        val fields = reports.single().fields
        assertTrue(fields["time"].toString().contains("source=30500"))
        assertTrue(fields["time"].toString().contains("elapsed=30000"))
        assertEquals(50f, fields["accuracy"])
        assertEquals(5, fields["locationType"])
        assertEquals("before_resume", fields["qualityReason"])
        assertEquals(2L, count(fields, "cb"))
        assertEquals(1L, count(fields, "reuse"))
    }

    @Test fun `successful completion is not classified using a later rejected callback`() {
        elapsed += 60_000
        diagnostics.onAcquisition(LocationAcquisition.Failure(LocationFailure.NETWORK, sample().copy(errorCode = 4)))
        diagnostics.onHealthTick(null)
        diagnostics.onCandidate("ACCURACY")
        diagnostics.onUploadSuccess(sample(), 20)
        val fields = reports.last().fields
        assertEquals("upload_success", fields["reason"])
        assertEquals(0, fields["sdkErrorCode"])
        assertEquals("ACCURACY", fields["qualityReason"])
        assertEquals(60_000L, fields["successGapMs"])
        assertEquals(0L, fields["maxSuccessGapMs"])
    }

    @Test fun `delayed result consumption preserves actual success time and remaining silence`() {
        diagnostics.onUploadSuccess(sample(), 10)
        elapsed += 80_000
        diagnostics.onUploadSuccess(sample(), 20, completedAtElapsedRealtime = 41_000L)
        elapsed += 10_000
        diagnostics.onHealthTick(null)
        assertEquals(1, reports.size) // Success completed 50 seconds ago, not 10 seconds ago.
        elapsed += 11_000
        diagnostics.onHealthTick(null)
        assertEquals(61_000L, reports.last().fields["successGapMs"])
        assertEquals(40_000L, reports.last().fields["maxSuccessGapMs"])
    }

    @Test fun `alternating failures and successes do not reopen remote first reports`() {
        diagnostics.onUploadSuccess(sample(), 10)
        repeat(10) {
            elapsed += 30_000
            diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
            diagnostics.onUploadStarted()
            diagnostics.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR, null,
                IllegalStateException("network unavailable"))
            diagnostics.onHealthTick(null)
            elapsed += 30_000
            diagnostics.onAcquisition(LocationAcquisition.Success(sample()))
            diagnostics.onUploadStarted()
            diagnostics.onUploadSuccess(sample(), 10)
            diagnostics.onHealthTick(null)
        }
        assertEquals(3, reports.size) // First success, first failure, one five-minute summary.
        assertEquals(1, reports.count { it.fields["stage"] == "first_success" })
        assertEquals(1, reports.count { it.fields["stage"] == "first" })
        diagnostics.finish()
        val fields = reports.last().fields
        assertEquals("healthy", fields["reason"])
        assertEquals("api_network,code=?,type=IllegalStateException", fields["apiFailure"])
        assertEquals(10L, count(fields, "fail"))
        assertEquals(11L, count(fields, "ok"))
        assertEquals(4, reports.size)
    }

    @Test fun `repeated callback gaps and recovery share the existing summary cadence`() {
        diagnostics.onUploadSuccess(sample(), 10)
        repeat(3) {
            elapsed += 90_000
            diagnostics.onHealthTick(null)
            diagnostics.onUploadSuccess(sample(), 10)
        }
        assertEquals(2, reports.size) // First success and the first observed gap only.
        assertEquals(1, reports.count { it.fields["stage"] == "gap" })
        elapsed += 120_000
        diagnostics.onHealthTick(null)
        assertEquals(3, reports.size)
        assertEquals("summary", reports.last().fields["stage"])
        assertEquals(90_000L, reports.last().fields["maxSuccessGapMs"])
        assertEquals(120_000L, reports.last().fields["successGapMs"])
        diagnostics.onUploadSuccess(sample(), 10)
        assertEquals(3, reports.size)
    }

    @Test fun `health before delayed consumption does not count open tail as a closed success interval`() {
        diagnostics.onUploadSuccess(sample(), 10)
        diagnostics.onUploadStarted()
        elapsed += 80_000
        diagnostics.onHealthTick(null)
        assertEquals("api_pending", reports.last().fields["reason"])
        assertEquals(80_000L, reports.last().fields["successGapMs"])
        assertEquals(0L, reports.last().fields["maxSuccessGapMs"])
        diagnostics.onUploadSuccess(sample(), 20, completedAtElapsedRealtime = 21_000L)
        assertEquals(2, reports.size)
        diagnostics.finish()
        assertEquals(20_000L, reports.last().fields["maxSuccessGapMs"])
        assertEquals(60_000L, reports.last().fields["successGapMs"])
    }

    @Test fun `cancellation counter records only a pending request and runtime keys are bounded`() {
        diagnostics = LocationReportingDiagnostics(17, "service-run", "3", { elapsed }, { uptime }) {
            mapOf("clientId" to "client", "runtime" to "idle=1", "sdkState" to "count=1",
                "unexpected" to "must not consume payload budget")
        }
        diagnostics.onUploadCancelled()
        diagnostics.onUploadStarted()
        diagnostics.onUploadCancelled()
        diagnostics.onUploadCancelled()
        diagnostics.finish("collector_interrupted")
        val fields = reports.single().fields
        assertEquals(1L, count(fields, "cancel"))
        assertEquals("client", fields["clientId"])
        assertEquals("count=1", fields["sdkState"])
        assertFalse(fields.containsKey("unexpected"))
        assertTrue(fields.size <= 25)
    }

    private fun count(fields: Map<String, Any?>, key: String): Long = fields.getValue("counts")
        .toString().split(',').associate { it.substringBefore('=') to it.substringAfter('=').toLong() }.getValue(key)

    private fun sample() = LocationResult(30.0, 120.0, "network", 50f, "GCJ02", 5, 2,
        locationTime = 100_000L, receivedAt = 100_000L, receivedElapsedRealtime = elapsed)
}
