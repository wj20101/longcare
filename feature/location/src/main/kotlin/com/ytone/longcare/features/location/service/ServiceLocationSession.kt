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
import kotlinx.coroutines.flow.conflate

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
    suspend fun run() {
        // Keep collecting/publishing AMap samples while a request is slow; retain only the latest.
        locations.conflate().collect { location ->
            currentCoroutineContext().ensureActive()
            if (!session.canUpload() || !evaluator.shouldUpload(location)) return@collect
            try {
                when (val result = uploadLocation(location)) {
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
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                    throwable = error,
                    extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to session.orderId),
                )
            }
        }
    }
}
