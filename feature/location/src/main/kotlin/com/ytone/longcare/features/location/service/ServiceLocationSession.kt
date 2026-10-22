package com.ytone.longcare.features.location.service

import android.os.SystemClock
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationQuality
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
import kotlinx.coroutines.isActive
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
    private val diagnostics: LocationReportingDiagnostics = LocationReportingDiagnostics(
        session.orderId, session.owner, session.account.userId.toString(), elapsedRealtime,
    ),
) {
    private data class AuthorizedSample(val location: LocationResult, val authorizationVersion: Long)
    private data class UploadCompletion(val outcome: Result<ApiResult<Unit>>, val completedAt: Long)

    private companion object {
        const val REPORTING_INTERVAL_MS = 30_000L
        // 整次请求最多占用一个采样周期；超时点不重试，只检查后续最新候选。
        const val UPLOAD_TIMEOUT_MS = 30_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() = coroutineScope {
        fun canUpload() = session.canUpload() && samplingEnabled.value.enabled
        var observerFailureReported = false

        fun reportResult(location: LocationResult, startedAt: Long, completion: UploadCompletion) {
            if (!canUpload()) return
            val result = try {
                completion.outcome.getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                diagnostics.onUploadFailure(LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR, null, error)
                return
            }
            when (result) {
                is ApiResult.Success -> {
                    diagnostics.onUploadSuccess(location,
                        (completion.completedAt - startedAt).coerceAtLeast(0L), completion.completedAt)
                    logI("位置实时上报成功 (orderId=${session.orderId})")
                }
                is ApiResult.Failure -> diagnostics.onUploadFailure(
                    LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR, result.code, null,
                )
                is ApiResult.Exception -> diagnostics.onUploadFailure(
                    LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR, null, result.exception,
                )
            }
            // Notification/UI failures cannot change a completed HTTP request's outcome.
            try {
                onUploadResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!observerFailureReported) {
                    observerFailureReported = true
                    runCatching {
                        LocationEventTracker.trackError(LocationEventTracker.EventType.REPORTING_TASK_ERROR,
                            extras = mapOf("orderId" to session.orderId, "sessionId" to session.owner,
                                "stage" to "upload_result_observer", "observerErrorType" to error.javaClass.simpleName),
                            userId = session.account.userId.toString())
                    }
                }
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
        var inFlight: Deferred<UploadCompletion>? = null
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
                diagnostics.onUploadCancelled()
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
                    val completion = if (upload.isCancelled) null else upload.await()
                    when {
                        timeoutRequested || (completion != null && completion.completedAt - inFlightStartedAt >= UPLOAD_TIMEOUT_MS) ->
                            reportResult(requireNotNull(inFlightLocation), inFlightStartedAt,
                                UploadCompletion(Result.success(ApiResult.Exception(SocketTimeoutException("location_upload_timeout"))),
                                    completion?.completedAt ?: now))
                        completion != null -> reportResult(requireNotNull(inFlightLocation), inFlightStartedAt, completion)
                        else -> diagnostics.onUploadCancelled()
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
                    val rejection = evaluator.rejection(location)
                    if (rejection != null) diagnostics.onRevalidationRejected(rejection.name)
                    if (rejection == null) {
                        currentCoroutineContext().ensureActive()
                        if (canUpload()) {
                            // Consumption and rate limiting occur only at actual dispatch, never at receipt.
                            evaluator.onUploadStarted(location)
                            inFlightStartedAt = elapsedRealtime()
                            lastUploadStartedAt = inFlightStartedAt
                            inFlightLocation = location
                            diagnostics.onUploadStarted()
                            inFlight = async(start = CoroutineStart.UNDISPATCHED) {
                                val outcome = try {
                                    currentCoroutineContext().ensureActive()
                                    if (!canUpload()) throw CancellationException("Location reporting paused")
                                    Result.success(withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { uploadLocation(location) }
                                        ?: ApiResult.Exception(SocketTimeoutException("location_upload_timeout")))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Result.failure(error)
                                }
                                // Capture before the queued consumer runs; its delay is not HTTP time.
                                UploadCompletion(outcome, elapsedRealtime())
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
                            val rejection = when {
                                !synchronizeGate() -> "sampling_paused"
                                pending.authorizationVersion != authorizationVersion -> "authorization_changed"
                                else -> evaluator.rejection(location)?.name ?: when {
                                    // The same source anchor handles queued callbacks and unknown-source fallback.
                                    LocationQuality.sampleElapsedRealtime(location) < samplingEpoch -> "before_resume"
                                    candidate != null && LocationQuality.sampleElapsedRealtime(location) <
                                        LocationQuality.sampleElapsedRealtime(requireNotNull(candidate)) -> "older_candidate"
                                    else -> null
                                }
                            }
                            diagnostics.onCandidate(rejection)
                            if (rejection == null) candidate = location
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
            diagnostics.onUploadCancelled()
            // An active order's collector was cancelled; this does not prove a process kill.
            diagnostics.finish(if (!currentCoroutineContext().isActive && session.isValid()) "collector_interrupted" else null)
        }
    }
}
