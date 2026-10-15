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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

/**
 * Service-owned upload executor. Business state is supplied by the session, never inferred
 * from an upload response. Failures are diagnostic-only: no Toast, state mutation or retry.
 */
internal class ServiceLocationSession(
    private val session: LocationReportingSession,
    private val locations: Flow<LocationResult>,
    private val uploadLocation: suspend (LocationResult) -> ApiResult<Unit>,
    private val evaluator: LocationSampleEvaluator,
) {
    suspend fun run() = coroutineScope {
        var uploadJob: Job? = null
        // Consume callbacks immediately. Busy samples are dropped, never queued for later replay.
        locations.collect { location ->
            if (uploadJob?.isActive == true) return@collect
            currentCoroutineContext().ensureActive()
            if (!session.canUpload() || !evaluator.shouldUpload(location)) return@collect
            uploadJob = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    // 校验期间也可能结束/切换订单，发请求前必须再次检查取消与会话。
                    currentCoroutineContext().ensureActive()
                    if (!session.canUpload()) return@launch
                    val result = uploadLocation(location)
                    // 不消费已结束会话的迟到 HTTP 结果，包括不响应取消的测试/第三方实现。
                    currentCoroutineContext().ensureActive()
                    if (!session.canUpload()) return@launch
                    when (result) {
                        is ApiResult.Success -> logI("位置实时上报成功 (orderId=${session.orderId})")
                        is ApiResult.Failure -> LocationEventTracker.trackError(
                            LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR,
                            extras = mapOf(
                                LocationEventTracker.Attribute.ORDER_ID to session.orderId,
                                LocationEventTracker.Attribute.ERROR_CODE to result.code,
                            ),
                        )
                        is ApiResult.Exception -> LocationEventTracker.trackError(
                            LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR,
                            throwable = result.exception,
                            extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId),
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
        }
    }
}
