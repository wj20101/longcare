package com.ytone.longcare.common.diagnostics

import android.content.Context
import com.tencent.bugly.crashreport.BuglyLog
import com.tencent.bugly.crashreport.CrashReport

internal interface CrashRuntime {
    fun initialize(context: Context, userId: String, crashFields: (String?, String?) -> Map<String, String>)
    fun setUserId(userId: String)
    fun recordBreadcrumb(message: String)
    fun postCaughtException(exception: DiagnosticException)
}

/** SDK-specific behavior stays here, including the 4.1.9.3 callback configuration. */
internal class BuglyRuntime : CrashRuntime {
    private var context: Context? = null

    override fun initialize(
        context: Context,
        userId: String,
        crashFields: (String?, String?) -> Map<String, String>,
    ) {
        val app = context.applicationContext
        val strategy = CrashReport.UserStrategy(app).apply {
            setCloseErrorCallback(false)
            setCrashHandleCallback(object : CrashReport.CrashHandleCallback() {
                override fun onCrashHandleStart(
                    crashType: Int,
                    errorType: String?,
                    errorMessage: String?,
                    errorStack: String?,
                ): MutableMap<String, String> = crashFields(errorType, errorMessage).toMutableMap()
            })
        }
        CrashReport.setUserId(app, userId)
        CrashReport.initCrashReport(app, strategy)
        this.context = app
    }

    override fun setUserId(userId: String) {
        CrashReport.setUserId(checkNotNull(context), userId)
    }

    override fun recordBreadcrumb(message: String) {
        BuglyLog.i("LongCare", message)
    }

    override fun postCaughtException(exception: DiagnosticException) {
        CrashReport.postCatchedException(exception)
    }
}
