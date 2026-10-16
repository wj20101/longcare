package com.ytone.longcare.features.location.service

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.reporting.LocationReportingSession
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException

/**
 * Service-owned upload executor. Business state is supplied by the session, never inferred
 * from an upload response. Failures update reporting health, never order state or sample replay.
 */
internal class ServiceLocationSession(
    private val session: LocationReportingSession,
    private val locations: Flow<LocationResult>,
    private val uploadLocation: suspend (LocationResult) -> ApiResult<Unit>,
    private val evaluator: LocationSampleEvaluator,
    private val onUploadResult: (ApiResult<Unit>) -> Unit = {},
) {
    private companion object {
        const val REPORTING_INTERVAL_MS = 30_000L
        // 整次请求最多占用一个采样周期；超时点不重试，下一周期只检查最新候选。
        const val UPLOAD_TIMEOUT_MS = 30_000L
    }

    suspend fun run() = coroutineScope {
        // Exactly one SDK subscription and one replaceable current candidate, never a history queue.
        val candidates = locations.buffer(Channel.CONFLATED).produceIn(this)
        try {
            for (location in candidates) {
                currentCoroutineContext().ensureActive()
                // Revalidate at the sampling instant: a candidate can age while HTTP is in flight.
                if (!session.canUpload() || !evaluator.shouldUpload(location)) continue
                val uploadJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    val startedAt = System.nanoTime()
                    try {
                        // 校验期间也可能结束/切换订单，发请求前必须再次检查取消与会话。
                        currentCoroutineContext().ensureActive()
                        if (!session.canUpload()) return@launch
                        val result = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { uploadLocation(location) }
                            ?: ApiResult.Exception(SocketTimeoutException("location_upload_timeout"))
                        // 不消费已结束会话的迟到 HTTP 结果，包括不响应取消的测试/第三方实现。
                        currentCoroutineContext().ensureActive()
                        if (!session.canUpload()) return@launch
                        onUploadResult(result)
                        val extras = mapOf(
                            LocationEventTracker.Attribute.ORDER_ID to session.orderId,
                            LocationEventTracker.Attribute.LOCATION_TIME to location.locationTime,
                            LocationEventTracker.Attribute.ACCURACY to location.accuracy,
                            "requestId" to location.requestId,
                            "durationMs" to (System.nanoTime() - startedAt) / 1_000_000,
                        )
                        when (result) {
                            is ApiResult.Success -> logI("位置实时上报成功 (orderId=${session.orderId})")
                            is ApiResult.Failure -> LocationEventTracker.trackError(
                                LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR,
                                extras = extras + (LocationEventTracker.Attribute.ERROR_CODE to result.code),
                            )
                            is ApiResult.Exception -> LocationEventTracker.trackError(
                                LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR,
                                throwable = result.exception,
                                extras = extras,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (!session.canUpload()) return@launch
                        LocationEventTracker.trackError(
                            LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                            throwable = error,
                            extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId),
                        )
                    }
                }
                // First valid sample is immediate. Later samples use one clock independent of SDK jitter
                // and HTTP duration; joining prevents concurrent requests at the timeout boundary.
                delay(REPORTING_INTERVAL_MS)
                uploadJob.join()
            }
        } finally { candidates.cancel() }
    }
}
