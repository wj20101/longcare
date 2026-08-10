package com.ytone.longcare.common.diagnostics

import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.common.utils.logE
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide boundary around the optional Bugly runtime.
 *
 * Debug builds intentionally do not initialize Bugly. Callers can still record local diagnostic
 * logs, while remote reporting remains a no-op until the application confirms initialization.
 */
object CrashReportGateway {
    private val initialized = AtomicBoolean(false)

    fun markInitialized() {
        initialized.set(true)
    }

    fun postCaughtException(exception: Throwable) {
        if (!initialized.get()) return

        try {
            CrashReport.postCatchedException(exception)
        } catch (reportingFailure: Throwable) {
            runCatching {
                logE(
                    message = "Remote crash reporting failed",
                    throwable = reportingFailure,
                )
            }
        }
    }
}
