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
