package com.ytone.longcare.common.diagnostics

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CrashReporterTest {
    private class Runtime : CrashRuntime {
        val identities = mutableListOf<String>()
        val errors = mutableListOf<DiagnosticException>()
        val breadcrumbs = mutableListOf<String>()
        var fail = false
        var calls = 0
        lateinit var callback: (String?, String?) -> Map<String, String>
        override fun initialize(context: Context, userId: String, crashFields: (String?, String?) -> Map<String, String>) {
            calls++
            if (fail) error("init failure")
            identities += userId
            callback = crashFields
        }
        override fun setUserId(userId: String) { identities += userId }
        override fun recordBreadcrumb(message: String) { breadcrumbs += message }
        override fun postCaughtException(exception: DiagnosticException) {
            if (fail) error("report failure")
            errors += exception
        }
    }
    private fun event(userId: String, code: String = "failed") = DiagnosticException(
        DiagnosticPayload.create("camera", code, "ERROR", userId, "失败", null, emptyMap()),
        IllegalStateException().apply { stackTrace = emptyArray() },
    )

    @Test
    fun `disabled reporting never touches SDK and late initialization uses raw identity`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime)
        reporter.setUserId(123)
        reporter.initialize(mockk(), false)
        reporter.postCaughtException(event("123"))
        reporter.recordBreadcrumb("step", "123")
        assertEquals(0, runtime.calls)
        assertTrue(runtime.identities.isEmpty())
        reporter.initialize(mockk(), true)
        assertEquals(listOf("123", "123"), runtime.identities)
        reporter.setUserId(null)
        assertEquals("0", runtime.identities.last())
        reporter.setUserId(456)
        assertEquals("456", runtime.identities.last())
    }

    @Test
    fun `late errors are dropped and asynchronous callbacks retain their own fields`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime)
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        val a = event("123")
        reporter.postCaughtException(a)
        reporter.setUserId(456)
        reporter.postCaughtException(a)
        val b = event("456", "other_failed")
        reporter.postCaughtException(b)
        assertEquals(listOf(a, b), runtime.errors)
        assertEquals("123", runtime.callback(DiagnosticException::class.java.name, a.message)["userId"])
        assertEquals("other_failed", runtime.callback(DiagnosticException::class.java.name, b.message)["eventCode"])
        assertEquals("456", runtime.callback("RealCrash", "message")["userId"])
    }

    @Test
    fun `rate limit expires and failing delivery can be retried`() {
        val runtime = Runtime()
        var now = 0L
        val reporter = CrashReporter(runtime) { now }
        reporter.initialize(mockk(), true)
        val event = event("0")
        runtime.fail = true
        reporter.postCaughtException(event)
        runtime.fail = false
        reporter.postCaughtException(event)
        reporter.postCaughtException(event)
        assertEquals(1, runtime.errors.size)
        now = 30_000_000_000L
        reporter.postCaughtException(event)
        assertEquals(2, runtime.errors.size)
    }

    @Test
    fun `sign modes stages and distinct samples are not suppressed as duplicate reports`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime)
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        fun sample(mode: String, stage: String, time: String) = DiagnosticException(
            event("123").fields + mapOf("orderId" to "42", "signInMode" to mode, "stage" to stage, "locationTime" to time),
            IllegalStateException().apply { stackTrace = emptyArray() },
        )
        val start = sample("START_ORDER", "scan", "1000")
        reporter.postCaughtException(start)
        reporter.postCaughtException(start)
        reporter.postCaughtException(sample("END_ORDER", "scan", "1000"))
        reporter.postCaughtException(sample("END_ORDER", "end_submit", "1000"))
        reporter.postCaughtException(sample("END_ORDER", "end_submit", "2000"))
        assertEquals(4, runtime.errors.size)
    }

    private fun continuousFailure(requestId: String, episode: Long, count: Long = 1L, stage: String = "first") =
        DiagnosticException(
            DiagnosticPayload.create("location", "amap_continuous_location_error", "ERROR", "123", "持续定位失败", null,
                mapOf("requestId" to requestId, "failureEpisode" to episode, "failureCount" to count,
                    "errorCode" to 4, "stage" to stage)),
            IllegalStateException().apply { stackTrace = emptyArray() },
        )

    @Test
    fun `new location clients retain their first failure within the duplicate window`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime) { 1_000_000_000L }
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        val first = continuousFailure("client-a", 1L)
        reporter.postCaughtException(first)
        reporter.postCaughtException(first)
        reporter.postCaughtException(continuousFailure("client-b", 1L))
        assertEquals(listOf("client-a", "client-b"), runtime.errors.map { it.fields["requestId"] })
    }

    @Test
    fun `new failure episodes retain their first report within the duplicate window`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime) { 1_000_000_000L }
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        reporter.postCaughtException(continuousFailure("client-a", 1L))
        reporter.postCaughtException(continuousFailure("client-a", 2L))
        reporter.postCaughtException(continuousFailure("client-a", 2L))
        assertEquals(listOf("1", "2"), runtime.errors.map { it.fields["failureEpisode"] })
    }

    @Test
    fun `new aggregate progress survives the duplicate window but the same summary is deduplicated`() {
        val runtime = Runtime()
        // Aggregation uses elapsedRealtime; deep sleep can advance it without advancing this clock.
        val reporter = CrashReporter(runtime) { 1_000_000_000L }
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        val first = continuousFailure("client-a", 1L, count = 2L, stage = "summary")
        val next = continuousFailure("client-a", 1L, count = 3L, stage = "summary")
        reporter.postCaughtException(first)
        reporter.postCaughtException(first)
        reporter.postCaughtException(next)
        reporter.postCaughtException(next)
        assertEquals(listOf("2", "3"), runtime.errors.map { it.fields["failureCount"] })
    }

    @Test
    fun `new attempts alarms and reporting sessions are distinct but repeated snapshots are suppressed`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime) { 1_000_000_000L }
        reporter.setUserId(123)
        reporter.initialize(mockk(), true)
        fun report(extras: Map<String, String>) = reporter.postCaughtException(DiagnosticException(
            event("123").fields + extras,
            IllegalStateException().apply { stackTrace = emptyArray() },
        ))
        val base = mapOf("attemptId" to "a", "sessionId" to "s", "plannedAt" to "1000",
            "reportSequence" to "1", "outcome" to "failed")
        report(base)
        report(base + ("durationMs" to "9000"))
        report(base + ("attemptId" to "b"))
        report(base + ("sessionId" to "t"))
        report(base + ("plannedAt" to "2000"))
        report(base + ("reportSequence" to "2"))
        report(base + ("outcome" to "recovered"))
        assertEquals(6, runtime.errors.size)
    }

    @Test
    fun `bounded duplicate history evicts old reports without suppressing a later operation`() {
        val runtime = Runtime()
        val reporter = CrashReporter(runtime) { 0L }
        reporter.initialize(mockk(), true)
        val events = (0..128).map { attempt ->
            DiagnosticException(event("0").fields + ("attemptId" to attempt.toString()),
                IllegalStateException().apply { stackTrace = emptyArray() })
        }
        events.forEach(reporter::postCaughtException)
        reporter.postCaughtException(events.last())
        reporter.postCaughtException(events.first())
        assertEquals(130, runtime.errors.size)
    }

    @Test
    fun `failed initialization is retryable and successful initialization is idempotent`() {
        val runtime = Runtime().apply { fail = true }
        val reporter = CrashReporter(runtime)
        reporter.initialize(mockk(), true)
        runtime.fail = false
        reporter.initialize(mockk(), true)
        reporter.initialize(mockk(), true)
        assertEquals(2, runtime.calls)
    }
}
