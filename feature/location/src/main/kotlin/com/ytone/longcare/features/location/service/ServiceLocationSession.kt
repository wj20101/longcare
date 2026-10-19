package com.ytone.longcare.features.location.service

import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.reporting.LocationReportingSession
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException

/** The Service records the real resume boundary before enabling SDK callbacks. */
internal data class LocationSamplingState(val enabled: Boolean, val resumedAtElapsedRealtime: Long = 0L)

/**
 * Service-owned upload executor. Business state is supplied by the session, never inferred
 * from an upload response. Failures update reporting health, never order state or sample replay.
 */
internal class ServiceLocationSession(
    private val session: LocationReportingSession,
    private val locations: Flow<LocationResult>,
    private val uploadLocation: suspend (LocationResult) -> ApiResult<Unit>,
    private val evaluator: LocationSampleEvaluator,
    private val samplingEnabled: StateFlow<LocationSamplingState>,
    private val onUploadResult: (ApiResult<Unit>) -> Unit = {},
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class AuthorizedSample(val location: LocationResult, val authorizationVersion: Long)

    private companion object {
        const val REPORTING_INTERVAL_MS = 30_000L
        // 整次请求最多占用一个采样周期；超时点不重试，只检查后续最新候选。
        const val UPLOAD_TIMEOUT_MS = 30_000L
        // Diagnostic threshold only: a successful HTTP upload after this gap is reported once.
        const val UPLOAD_SUCCESS_GAP_DIAGNOSTIC_MS = 90_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() = coroutineScope {
        val diagnosticUserId = DiagnosticEventTracker.currentUserId()
        var lastSuccessfulUploadElapsed: Long? = null
        var uploadFailed = false
        fun canUpload() = session.canUpload() && samplingEnabled.value.enabled

        fun reportResult(location: LocationResult, startedAt: Long, outcome: Result<ApiResult<Unit>>) {
            if (!canUpload()) return
            try {
                val result = outcome.getOrThrow()
                onUploadResult(result)
                val extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to session.orderId,
                    LocationEventTracker.Attribute.LOCATION_TIME to location.locationTime,
                    LocationEventTracker.Attribute.ACCURACY to location.accuracy,
                    "requestId" to location.requestId,
                    "durationMs" to (elapsedRealtime() - startedAt).coerceAtLeast(0L),
                )
                when (result) {
                    is ApiResult.Success -> {
                        val succeededAt = elapsedRealtime()
                        val gap = lastSuccessfulUploadElapsed?.let { (succeededAt - it).coerceAtLeast(0L) }
                        val stage = when {
                            uploadFailed -> "upload_recovered"
                            lastSuccessfulUploadElapsed == null -> "first_success"
                            gap != null && gap >= UPLOAD_SUCCESS_GAP_DIAGNOSTIC_MS -> "gap_resumed"
                            else -> null
                        }
                        lastSuccessfulUploadElapsed = succeededAt
                        uploadFailed = false
                        logI("位置实时上报成功 (orderId=${session.orderId})")
                        if (stage != null) LocationEventTracker.trackEvent(
                            LocationEventTracker.EventType.API_UPLOAD_RESUMED,
                            extras = extras + mapOf("stage" to stage, "successGapMs" to gap),
                            reportToServer = true,
                            userId = diagnosticUserId,
                        )
                    }
                    is ApiResult.Failure -> {
                        uploadFailed = true
                        LocationEventTracker.trackError(
                            LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR,
                            extras = extras + (LocationEventTracker.Attribute.ERROR_CODE to result.code),
                            userId = diagnosticUserId,
                        )
                    }
                    is ApiResult.Exception -> {
                        uploadFailed = true
                        LocationEventTracker.trackError(
                            LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR,
                            throwable = result.exception,
                            extras = extras,
                            userId = diagnosticUserId,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!canUpload()) return
                uploadFailed = true
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                    throwable = error,
                    extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId),
                    userId = diagnosticUserId,
                )
            }
        }

        // One SDK subscription; the mailbox replaces bursts rather than queueing historical points.
        val samples = locations.map { AuthorizedSample(it, session.readyState.value.version) }
            .buffer(Channel.CONFLATED).produceIn(this)
        val readyChanges = session.readyState.buffer(Channel.CONFLATED).produceIn(this)
        val samplingChanges = samplingEnabled.buffer(Channel.CONFLATED).produceIn(this)
        var candidate: LocationResult? = null
        var sourceFinished = false
        var samplingEpoch = samplingEnabled.value.resumedAtElapsedRealtime
        var authorizationVersion = session.readyState.value.version
        var lastUploadStartedAt: Long? = null
        var inFlight: Deferred<Result<ApiResult<Unit>>>? = null
        var inFlightLocation: LocationResult? = null
        var inFlightStartedAt = 0L
        var timeoutRequested = false
        fun synchronizeGate(): Boolean {
            val sampling = samplingEnabled.value
            val authorization = session.readyState.value
            val authorizationChanged = authorization.version != authorizationVersion
            authorizationVersion = authorization.version
            val resumed = sampling.resumedAtElapsedRealtime != samplingEpoch
            if (resumed) {
                // StateFlow may conflate pause/resume, but its new epoch still invalidates old work.
                samplingEpoch = sampling.resumedAtElapsedRealtime
            }
            val enabled = session.canUpload() && sampling.enabled
            if (resumed || authorizationChanged || !enabled) {
                candidate = null
                timeoutRequested = false
                inFlight?.cancel()
                // A response may already have completed before this state change was consumed.
                if (inFlight?.isCompleted == true) {
                    inFlight = null
                    inFlightLocation = null
                }
            }
            return enabled
        }
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val enabled = synchronizeGate()
                val now = elapsedRealtime()

                val upload = inFlight
                if (upload != null && upload.isCompleted) {
                    currentCoroutineContext().ensureActive()
                    when {
                        timeoutRequested || (!upload.isCancelled && now - inFlightStartedAt >= UPLOAD_TIMEOUT_MS) ->
                            reportResult(requireNotNull(inFlightLocation), inFlightStartedAt,
                            Result.success(ApiResult.Exception(SocketTimeoutException("location_upload_timeout"))))
                        !upload.isCancelled -> reportResult(requireNotNull(inFlightLocation), inFlightStartedAt, upload.await())
                    }
                    inFlight = null
                    inFlightLocation = null
                    timeoutRequested = false
                } else if (upload != null && !upload.isCancelled && enabled && !timeoutRequested &&
                    now - inFlightStartedAt >= UPLOAD_TIMEOUT_MS) {
                    // Handler delays can stop during deep sleep. Any callback also checks the real deadline.
                    timeoutRequested = true
                    upload.cancel()
                }

                val remaining = lastUploadStartedAt?.let { (REPORTING_INTERVAL_MS - (now - it)).coerceAtLeast(0L) } ?: 0L
                if (enabled && inFlight == null && candidate != null && remaining == 0L) {
                    val location = requireNotNull(candidate)
                    candidate = null
                    if (evaluator.isEligible(location)) {
                        currentCoroutineContext().ensureActive()
                        if (canUpload()) {
                            // Consumption and rate limiting occur only at actual dispatch, never at receipt.
                            evaluator.onUploadStarted(location)
                            inFlightStartedAt = elapsedRealtime()
                            lastUploadStartedAt = inFlightStartedAt
                            inFlightLocation = location
                            inFlight = async(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    currentCoroutineContext().ensureActive()
                                    if (!canUpload()) throw CancellationException("Location reporting paused")
                                    Result.success(withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { uploadLocation(location) }
                                        ?: ApiResult.Exception(SocketTimeoutException("location_upload_timeout")))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Result.failure(error)
                                }
                            }
                            continue
                        }
                    }
                }
                if (sourceFinished && candidate == null && inFlight == null) break

                select<Unit> {
                    // State transitions win a ready sample/HTTP completion at the same dispatch boundary.
                    readyChanges.onReceive { }
                    samplingChanges.onReceive { }
                    if (!sourceFinished) samples.onReceiveCatching { received ->
                        val pending = received.getOrNull()
                        if (pending == null) {
                            received.exceptionOrNull()?.let { throw it }
                            sourceFinished = true
                        } else {
                            val location = pending.location
                            if (synchronizeGate() && pending.authorizationVersion == authorizationVersion &&
                                location.receivedElapsedRealtime >= samplingEpoch &&
                                (candidate == null || location.locationTime >= requireNotNull(candidate).locationTime) &&
                                evaluator.isEligible(location)) {
                                candidate = location
                            }
                        }
                    }
                    inFlight?.onJoin { }
                    val waitingUpload = inFlight
                    when {
                        waitingUpload != null && !waitingUpload.isCancelled && enabled && !timeoutRequested ->
                            onTimeout((UPLOAD_TIMEOUT_MS - (now - inFlightStartedAt)).coerceAtLeast(1L)) { }
                        waitingUpload == null && enabled && candidate != null -> onTimeout(remaining.coerceAtLeast(1L)) { }
                    }
                }
            }
        } finally {
            samples.cancel()
            readyChanges.cancel()
            samplingChanges.cancel()
            inFlight?.cancel()
        }
    }
}
