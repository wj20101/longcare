package com.ytone.longcare.common.diagnostics

import android.content.Context
import com.ytone.longcare.common.utils.logE

/** The only owner of Bugly initialization, user identity, context and delivery. */
object CrashReportGateway {
    private val reporter = CrashReporter(BuglyRuntime())
    val userId: String get() = reporter.userId

    fun initialize(context: Context, enabled: Boolean) = reporter.initialize(context, enabled)
    fun setUserId(value: Int?) = reporter.setUserId(value)
    fun recordBreadcrumb(message: String, userId: String) = reporter.recordBreadcrumb(message, userId)
    fun postCaughtException(exception: DiagnosticException) = reporter.postCaughtException(exception)
}

internal class CrashReporter(
    private val runtime: CrashRuntime,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private var initialized = false
    private val recentErrors = LinkedHashMap<List<String>, Long>()

    /** Matches the API's logged-out ID. Never concatenate or hash a business user ID. */
    @Volatile
    var userId: String = "0"
        private set

    fun initialize(context: Context, enabled: Boolean): Unit = synchronized(lock) {
        if (!enabled || initialized) return@synchronized
        try {
            runtime.initialize(context, userId) { errorType, errorMessage ->
                // Decode this event, never a global "last event" map. No IO or SDK calls.
                if (errorType == DiagnosticException::class.java.name) {
                    DiagnosticPayload.decode(errorMessage.orEmpty())
                } else {
                    mapOf("category" to "runtime", "userId" to userId)
                }
            }
            runtime.setUserId(userId)
            initialized = true
        } catch (_: Throwable) {
            localFailure()
        }
    }

    fun setUserId(value: Int?): Unit = synchronized(lock) {
        val next = value?.toString() ?: "0"
        if (next != userId) recentErrors.clear()
        userId = next
        if (initialized) {
            try {
                runtime.setUserId(next)
            } catch (_: Throwable) {
                localFailure()
            }
        }
    }

    fun recordBreadcrumb(message: String, userId: String): Unit = synchronized(lock) {
        if (!initialized || userId != this.userId) return@synchronized
        try {
            runtime.recordBreadcrumb(message)
        } catch (_: Throwable) {
            localFailure()
        }
    }

    fun postCaughtException(exception: DiagnosticException): Unit = synchronized(lock) {
        if (!initialized) return@synchronized
        // Do not submit a late callback belonging to a previous account as the current user.
        if (exception.fields["userId"] != userId) return@synchronized
        val fields = exception.fields
        // Requests and aggregated failure episodes carry their own identity/progress. Only the
        // same report is a duplicate; a new client, episode or summary must keep its evidence.
        val key = REPORT_IDENTITY_FIELDS
            .map { fields[it].orEmpty() } + exception.stackTrace.take(4).map { it.toString() }
        val now = nanoTime()
        if (recentErrors[key]?.let { now - it in 0 until DUPLICATE_WINDOW_NS } == true) return@synchronized
        try {
            runtime.postCaughtException(exception)
            recentErrors[key] = now
            if (recentErrors.size > MAX_RECENT_REPORTS) recentErrors.remove(recentErrors.keys.first())
        } catch (_: Throwable) {
            localFailure()
        }
    }

    private fun localFailure() {
        // Never recursively report diagnostic failures or expose SDK exception messages.
        runCatching { logE("Remote diagnostics unavailable") }
    }

    private companion object {
        const val DUPLICATE_WINDOW_NS = 30_000_000_000L
        const val MAX_RECENT_REPORTS = 128

        // Keep distinct attempts, alarm deliveries and aggregate progress. Volatile duration and
        // runtime fields deliberately do not defeat duplicate suppression for the same report.
        val REPORT_IDENTITY_FIELDS = listOf(
            "category", "eventCode", "level", "userId", "errorType", "errorCode", "failureCode",
            "orderId", "signInMode", "stage", "outcome", "reason", "locationTime",
            "requestId", "actionId", "attemptId", "sessionId", "alarmId", "plannedAt",
            "failureEpisode", "failureCount", "reportSequence",
        )
    }
}
