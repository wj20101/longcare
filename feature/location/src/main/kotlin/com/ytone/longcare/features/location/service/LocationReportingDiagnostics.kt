package com.ytone.longcare.features.location.service

import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.locationTimeDiagnostic
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult

/** One service run's bounded counters. The existing health loop supplies ticks; this never schedules work. */
internal class LocationReportingDiagnostics(
    private val orderId: Long,
    private val sessionId: String,
    private val userId: String,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
    private val runtimeSnapshot: () -> Map<String, Any?> = { emptyMap() },
) {
    private val startedAt = elapsedRealtime()
    private var lastSuccess: Long? = null
    private var lastSample: LocationResult? = null
    private var newestSourceElapsed = 0L
    private var hasEligibleSample = false
    private var pendingAt: Long? = null
    private var callbackCount = 0L
    private var rejectedCount = 0L
    private var failureCount = 0L
    private var successCount = 0L
    private var cancelledCount = 0L
    private var sourceRepeatCount = 0L
    private var apiErrorCode: Int? = null
    private var apiErrorType: String? = null
    private var lastApiFailure: String? = null
    private var qualityReason: String? = null
    private var gateReason: String? = null
    private var lastTickElapsed = startedAt
    private var lastTickUptime = uptimeMillis()
    private var maxTickGap = 0L
    private var maxSleepGap = 0L
    private var maxSuccessGap = 0L
    private var hasReportedIssue = false
    private var reportedAt = startedAt
    private var reportSequence = 0L
    private var finished = false

    fun onAcquisition(event: LocationAcquisition) = safely {
        val sample = when (event) {
            is LocationAcquisition.Success -> event.location
            is LocationAcquisition.Failure -> event.location
        } ?: return@safely // A pause marker is not an SDK callback.
        callbackCount++
        lastSample = sample
        if (sample.errorCode == 0 && sample.sourceElapsedRealtime > 0L) {
            if (sample.sourceElapsedRealtime <= newestSourceElapsed) sourceRepeatCount++
            newestSourceElapsed = maxOf(newestSourceElapsed, sample.sourceElapsedRealtime)
        }
    }

    /** Called once at mailbox receipt, not again when a waiting candidate is revalidated. */
    fun onCandidate(rejection: String?) = safely {
        qualityReason = rejection
        if (rejection == null) hasEligibleSample = true else rejectedCount++
    }

    fun onRevalidationRejected(reason: String) = safely { qualityReason = reason }

    fun onUploadStarted() = safely { pendingAt = elapsedRealtime(); gateReason = null }
    fun onUploadCancelled() = safely {
        if (pendingAt != null) cancelledCount++
        pendingAt = null
    }

    fun onUploadFailure(type: LocationEventTracker.EventType, errorCode: Int?, error: Throwable?) = safely {
        pendingAt = null
        failureCount++
        lastApiFailure = when (type) {
            LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR -> "api_business"
            LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR -> "api_network"
            else -> "api_adapter"
        }
        // Failure classes remain visible in the next aggregate without one remote exception per point.
        apiErrorCode = errorCode
        apiErrorType = error?.javaClass?.simpleName
        if (!hasReportedIssue || due()) {
            val stage = if (hasReportedIssue) "summary" else "first"
            hasReportedIssue = true
            report(type, stage, error)
        }
    }

    fun onUploadSuccess(location: LocationResult, durationMs: Long,
        completedAtElapsedRealtime: Long = elapsedRealtime()) = safely {
        val now = elapsedRealtime()
        // Main may consume an already-completed response after a scheduling/sleep delay.
        // Measure server success at completion, not at delayed consumption of that result.
        val completedAt = completedAtElapsedRealtime.coerceIn(lastSuccess ?: startedAt, now)
        val gap = completedAt - (lastSuccess ?: startedAt)
        // Only completed success-to-success intervals are closed observations. The current
        // tail remains in successGapMs; a queued HTTP result can still shorten that tail.
        if (lastSuccess != null) maxSuccessGap = maxOf(maxSuccessGap, gap)
        val firstSuccess = lastSuccess == null
        successCount++
        lastSuccess = completedAt
        pendingAt = null
        if (firstSuccess) report(
            LocationEventTracker.EventType.API_UPLOAD_RESUMED, "first_success",
            successGapMs = gap, requestMs = durationMs, sample = location,
        )
    }

    /** Observes Doze/paused sessions too. A delayed tick describes what was observed after resumption. */
    fun onHealthTick(pausedReason: String?) = safely {
        gateReason = pausedReason
        val now = elapsedRealtime()
        val uptime = uptimeMillis()
        val tickGap = (now - lastTickElapsed).coerceAtLeast(0L)
        maxTickGap = maxOf(maxTickGap, tickGap)
        maxSleepGap = maxOf(maxSleepGap, (tickGap - (uptime - lastTickUptime).coerceAtLeast(0L)).coerceAtLeast(0L))
        lastTickElapsed = now
        lastTickUptime = uptime
        if (successGap(now) >= SUCCESS_GAP_MS && !hasReportedIssue) {
            hasReportedIssue = true
            report(LocationEventTracker.EventType.REPORTING_HEALTH, "gap")
        } else if (due()) report(LocationEventTracker.EventType.REPORTING_HEALTH, "summary")
    }

    fun finish(reason: String? = null) = safely {
        finished = true
        if (reason != null) gateReason = reason
        // A healthy run needs a bounded final observation too. This describes the collector,
        // not completion of the care order; interruptions retain their explicit reason.
        report(LocationEventTracker.EventType.REPORTING_HEALTH, "finished")
    }

    private fun due(): Boolean = elapsedRealtime() - reportedAt >= SUMMARY_INTERVAL_MS

    private fun successGap(now: Long) = (now - (lastSuccess ?: startedAt)).coerceAtLeast(0L)

    private fun report(type: LocationEventTracker.EventType, stage: String, error: Throwable? = null,
        successGapMs: Long? = null, requestMs: Long? = null, sample: LocationResult? = lastSample) {
        val now = elapsedRealtime()
        val wallNow = System.currentTimeMillis()
        val rawAge = lastSample?.receivedElapsedRealtime?.takeIf { it > 0L }?.let { (now - it).coerceAtLeast(0L) }
        val gap = successGap(now)
        val resumed = type == LocationEventTracker.EventType.API_UPLOAD_RESUMED
        val apiFailed = type == LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR ||
            type == LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR ||
            type == LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR
        val reason = when {
            resumed -> "upload_success"
            apiFailed -> lastApiFailure
            gateReason != null -> gateReason
            lastSuccess != null && gap < SUCCESS_GAP_MS -> "healthy"
            pendingAt != null -> "api_pending"
            lastSample == null -> "no_callback"
            rawAge != null && rawAge >= SUCCESS_GAP_MS -> "callback_silence"
            lastSample?.errorCode != null && lastSample?.errorCode != 0 -> "sdk_error"
            qualityReason != null -> "sample_rejected"
            hasEligibleSample -> "awaiting_dispatch"
            else -> "no_eligible_sample"
        }
        reportSequence++
        val extras = linkedMapOf<String, Any?>(
            "orderId" to orderId, "sessionId" to sessionId, "stage" to stage, "reason" to reason,
            "reportSequence" to reportSequence,
            "successGapMs" to (successGapMs ?: gap),
            "maxSuccessGapMs" to maxSuccessGap,
            "observedMs" to (now - startedAt).coerceAtLeast(0L),
            "requestMs" to (requestMs ?: pendingAt?.let { (now - it).coerceAtLeast(0L) }),
            "time" to sample?.let { locationTimeDiagnostic(it, wallNow, now,
                LocationQuality.timeIssue(it, wallNow, now)?.name) },
            "accuracy" to sample?.accuracy?.takeIf { it.isFinite() },
            "locationType" to sample?.locationType,
            "sdkErrorCode" to sample?.errorCode, "qualityReason" to qualityReason,
            "apiFailure" to lastApiFailure?.let { "$it,code=${apiErrorCode ?: "?"},type=${apiErrorType ?: "?"}" },
            "maxTickGapMs" to maxTickGap, "maxSleepGapMs" to maxSleepGap,
            // Fixed labels and lifetime counters fit one bounded scalar; differences give window counts.
            "counts" to "cb=$callbackCount,rej=$rejectedCount,ok=$successCount,fail=$failureCount,cancel=$cancelledCount,reuse=$sourceRepeatCount",
        ) + runCatching(runtimeSnapshot).getOrDefault(emptyMap())
            .filterKeys { it == "clientId" || it == "runtime" || it == "sdkState" }
        // Advance before delivery so an unavailable reporter cannot cause a retry storm.
        reportedAt = now
        maxTickGap = 0L
        maxSleepGap = 0L
        maxSuccessGap = 0L
        // Retain transient failures through successful requests until a report includes them.
        // Clearing at observation, rather than at every recovery, prevents silent evidence loss.
        lastApiFailure = null
        apiErrorCode = null
        apiErrorType = null
        runCatching {
            if (error != null || type == LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR) {
                LocationEventTracker.trackError(type, error, extras, userId)
            } else LocationEventTracker.trackEvent(type, extras, reportToServer = true, userId = userId)
        }
    }

    private inline fun safely(block: () -> Unit) {
        if (finished) return
        try { block() } catch (_: Exception) { /* Diagnostics never interrupt collection or HTTP. */ }
    }

    private companion object {
        const val SUCCESS_GAP_MS = 60_000L // Business observability target, not a new upload interval.
        const val SUMMARY_INTERVAL_MS = 5 * 60_000L
    }
}
