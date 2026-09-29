package com.ytone.longcare.common.diagnostics

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import java.net.URI
import java.util.concurrent.CancellationException

/** Single policy/formatting entry point for all business diagnostic events. */
object DiagnosticEventTracker {
    /** Capture at task start when a callback can outlive the signed-in user. */
    fun currentUserId(): String = CrashReportGateway.userId

    fun trackEvent(
        category: DiagnosticCategory,
        event: String,
        description: String,
        extras: Map<String, Any?> = emptyMap(),
        userId: String = currentUserId(),
    ) {
        report(category, event, description, null, extras, userId, isError = false)
    }

    fun trackError(
        category: DiagnosticCategory,
        event: String,
        description: String,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
        userId: String = currentUserId(),
    ) {
        // Cancellation is control flow, never a remote error.
        if (throwable is CancellationException) return
        report(category, event, description, throwable, extras, userId, isError = true)
    }

    fun safeUrlExtras(url: String): Map<String, Any?> = try {
        val uri = URI(url)
        mapOf("urlScheme" to uri.scheme, "urlHost" to uri.host, "urlPathLength" to (uri.rawPath?.length ?: 0))
    } catch (_: Exception) {
        mapOf("urlValid" to false, "urlLength" to url.length)
    }

    private fun report(
        category: DiagnosticCategory,
        event: String,
        description: String,
        throwable: Throwable?,
        extras: Map<String, Any?>,
        userId: String,
        isError: Boolean,
    ) {
        try {
            val fields = DiagnosticPayload.create(
                category.code, event, if (isError) "ERROR" else "INFO", userId, description, throwable, extras,
            )
            val text = DiagnosticPayload.encode(fields)
            // Use the same sanitized payload locally and remotely; never log the raw cause here.
            runCatching { if (isError) logE(text) else logI(text) }
            if (isError) {
                CrashReportGateway.postCaughtException(DiagnosticException(fields, throwable))
            } else {
                CrashReportGateway.recordBreadcrumb(text, userId)
            }
        } catch (_: Exception) {
            runCatching { logE("Diagnostic event could not be recorded") }
        }
    }
}
