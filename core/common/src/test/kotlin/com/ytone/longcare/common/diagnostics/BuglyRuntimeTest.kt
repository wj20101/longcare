package com.ytone.longcare.common.diagnostics

import android.content.Context
import com.tencent.bugly.crashreport.CrashReport
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BuglyRuntimeTest {
    @After
    fun cleanup() { unmockkAll() }

    @Test
    fun `raw user ID reaches Bugly and event fields are enabled for caught errors`() {
        mockkStatic(CrashReport::class)
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { CrashReport.setUserId(context, any()) } just Runs
        val strategy = slot<CrashReport.UserStrategy>()
        every { CrashReport.initCrashReport(context, capture(strategy)) } just Runs
        val runtime = BuglyRuntime()
        runtime.initialize(context, "123") { _, _ -> mapOf("userId" to "123", "category" to "camera") }
        runtime.setUserId("456")
        runtime.setUserId("0")
        verifyOrder {
            CrashReport.setUserId(context, "123")
            CrashReport.initCrashReport(context, any<CrashReport.UserStrategy>())
            CrashReport.setUserId(context, "456")
            CrashReport.setUserId(context, "0")
        }
        assertFalse(strategy.captured.closeErrorCallback)
        val fields = strategy.captured.crashHandleCallback.onCrashHandleStart(1, "type", "message", "stack")
        assertEquals("123", fields["userId"])
        assertEquals("camera", fields["category"])
    }
}
