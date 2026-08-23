package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.features.location.manager.LocationSampleStore
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 订单进行期间的实时定位上报入口。
 *
 * 定位点只在当前进程、当前订单会话内上传：不落库、不排队、不跨进程补传。
 * 单个点上传失败后直接丢弃，由下一次定位回调继续上报最新位置。
 */
@Singleton
class LocationReportingManager @Inject constructor(
    private val locationFacade: LocationFacade,
    private val locationSampleStore: LocationSampleStore,
    private val locationRepository: LocationRepository,
    private val clock: LocationClock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lifecycleLock = Any()
    private var reportingJob: Job? = null
    private var currentOwner: String? = null
    private var currentOrderKey: OrderKey? = null
    private var generation: Long = 0L
    private var lastObservedLocation: LocationResult? = null
    private var lastObservedLocationTimeMs: Long? = null
    private var lastSampleDiagnosticTimeMs: Long = 0L
    private var lastJumpDiagnosticTimeMs: Long = 0L

    fun startReporting(orderKey: OrderKey) {
        synchronized(lifecycleLock) {
            if (
                currentOrderKey?.orderId == orderKey.orderId &&
                reportingJob?.isActive == true
            ) {
                currentOwner?.let(locationFacade::acquireKeepAlive)
                return
            }

            stopReportingLocked()
            val owner = buildOwner(orderKey)
            val startedAt = clock.currentTimeMillis()
            generation += 1
            val activeGeneration = generation
            currentOwner = owner
            currentOrderKey = orderKey
            resetLocationDiagnostics()
            locationFacade.acquireKeepAlive(owner)

            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    locationSampleStore.continuousLocations.collect { location ->
                        processSample(
                            orderId = orderKey.orderId,
                            sessionStart = startedAt,
                            location = location,
                        )
                    }
                } catch (_: CancellationException) {
                    logI("位置采集任务已取消")
                } catch (error: Exception) {
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.REPORTING_TASK_ERROR,
                        throwable = error,
                        extras = mapOf(
                            LocationEventTracker.Attribute.ERROR_TYPE to
                                error.javaClass.simpleName,
                        ),
                    )
                } finally {
                    finishGeneration(activeGeneration, owner, orderKey.orderId)
                }
            }
            reportingJob = job
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.REPORTING_START,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderKey.orderId,
                    LocationEventTracker.Attribute.GENERATION to activeGeneration,
                ),
            )
            job.start()
        }
    }

    fun stopReporting() {
        synchronized(lifecycleLock) { stopReportingLocked() }
    }

    private suspend fun processSample(
        orderId: Long,
        sessionStart: Long,
        location: LocationResult,
    ) {
        val receivedAt = clock.currentTimeMillis()
        val validated = LocationSampleValidator.validate(location, sessionStart, receivedAt)
        if (validated == null) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.LOCATION_INVALID_SKIPPED,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.PROVIDER to location.provider,
                ),
            )
            return
        }

        trackLocationDiagnostics(
            orderId = orderId,
            location = location,
            capturedAt = validated.capturedAt,
            now = receivedAt,
        )
        uploadCurrentSample(orderId, location)
    }

    private suspend fun uploadCurrentSample(orderId: Long, location: LocationResult) {
        try {
            when (
                val result = locationRepository.addPosition(
                    orderId = orderId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                )
            ) {
                is ApiResult.Success -> logI("位置实时上报成功 (orderId=$orderId)")
                is ApiResult.Failure -> LocationEventTracker.trackError(
                    LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR,
                    extras = mapOf(
                        LocationEventTracker.Attribute.ORDER_ID to orderId,
                        LocationEventTracker.Attribute.ERROR_CODE to result.code,
                    ),
                )
                is ApiResult.Exception -> LocationEventTracker.trackError(
                    LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR,
                    throwable = result.exception,
                    extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to orderId),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                throwable = error,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.ERROR_TYPE to error.javaClass.simpleName,
                ),
            )
        }
    }

    private fun finishGeneration(expectedGeneration: Long, owner: String, orderId: Long) {
        synchronized(lifecycleLock) {
            if (generation != expectedGeneration || currentOwner != owner) return
            reportingJob = null
            currentOwner = null
            currentOrderKey = null
            resetLocationDiagnostics()
            locationFacade.releaseKeepAlive(owner)
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.REPORTING_STOP,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.GENERATION to expectedGeneration,
                ),
            )
        }
    }

    private fun stopReportingLocked() {
        generation += 1
        val job = reportingJob
        val owner = currentOwner
        val orderId = currentOrderKey?.orderId
        val wasActive = owner != null || job?.isActive == true
        reportingJob = null
        currentOwner = null
        currentOrderKey = null
        resetLocationDiagnostics()
        job?.cancel()
        owner?.let(locationFacade::releaseKeepAlive)
        if (wasActive) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.REPORTING_STOP,
                extras = mapOf(LocationEventTracker.Attribute.ORDER_ID to orderId),
            )
        }
    }

    private fun trackLocationDiagnostics(
        orderId: Long,
        location: LocationResult,
        capturedAt: Long,
        now: Long,
    ) {
        maybeTrackSample(orderId, location, now)
        maybeTrackJump(orderId, location, capturedAt, now)
        lastObservedLocation = location
        lastObservedLocationTimeMs = capturedAt
    }

    private fun maybeTrackSample(orderId: Long, location: LocationResult, now: Long) {
        if (
            lastSampleDiagnosticTimeMs != 0L &&
            now - lastSampleDiagnosticTimeMs < SAMPLE_DIAGNOSTIC_INTERVAL_MS
        ) return
        LocationEventTracker.trackLocationSample(
            LocationEventTracker.EventType.LOCATION_SAMPLE_RECORDED,
            orderId,
            location,
            extras = mapOf(
                LocationEventTracker.Attribute.SAMPLE_REASON to if (
                    lastSampleDiagnosticTimeMs == 0L
                ) {
                    LocationEventTracker.SampleReason.FIRST.telemetryValue
                } else {
                    LocationEventTracker.SampleReason.PERIODIC.telemetryValue
                },
            ),
        )
        lastSampleDiagnosticTimeMs = now
    }

    private fun maybeTrackJump(
        orderId: Long,
        location: LocationResult,
        capturedAt: Long,
        now: Long,
    ) {
        val previous = lastObservedLocation ?: return
        val previousTime = lastObservedLocationTimeMs ?: return
        val elapsedSeconds = (capturedAt - previousTime) / 1_000.0
        if (elapsedSeconds <= 0.0) return
        val distance = distanceMeters(previous, location)
        val speed = distance / elapsedSeconds
        val suspicious = distance >= FORCE_REPORT_JUMP_DISTANCE_M ||
            (distance >= SUSPICIOUS_JUMP_DISTANCE_M && speed >= SUSPICIOUS_SPEED_MPS)
        if (!suspicious || now - lastJumpDiagnosticTimeMs < JUMP_DIAGNOSTIC_INTERVAL_MS) return
        LocationEventTracker.trackLocationSample(
            LocationEventTracker.EventType.LOCATION_JUMP_DETECTED,
            orderId,
            location,
            extras = mapOf(
                LocationEventTracker.Attribute.DISTANCE_METERS to distance.formatOneDecimal(),
                LocationEventTracker.Attribute.ELAPSED_SECONDS to elapsedSeconds.formatOneDecimal(),
                LocationEventTracker.Attribute.SPEED_METERS_PER_SECOND to speed.formatOneDecimal(),
            ),
        )
        lastJumpDiagnosticTimeMs = now
    }

    private fun resetLocationDiagnostics() {
        lastObservedLocation = null
        lastObservedLocationTimeMs = null
        lastSampleDiagnosticTimeMs = 0L
        lastJumpDiagnosticTimeMs = 0L
    }

    private fun distanceMeters(start: LocationResult, end: LocationResult): Double {
        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val haversine = sin(deltaLat / 2).pow(2) +
            cos(startLat) * cos(endLat) * sin(deltaLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    private fun buildOwner(orderKey: OrderKey): String = "location_report_${orderKey.orderId}"

    private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)

    private companion object {
        const val SAMPLE_DIAGNOSTIC_INTERVAL_MS = 5L * 60 * 1_000
        const val JUMP_DIAGNOSTIC_INTERVAL_MS = 60_000L
        const val SUSPICIOUS_JUMP_DISTANCE_M = 500.0
        const val FORCE_REPORT_JUMP_DISTANCE_M = 1_500.0
        const val SUSPICIOUS_SPEED_MPS = 30.0
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}

@Singleton
class LocationClock @Inject constructor() {
    fun currentTimeMillis(): Long = System.currentTimeMillis()
}

internal object LocationSampleValidator {
    private const val MAX_SAMPLE_AGE_MILLIS = 2L * 60 * 1_000
    private const val MAX_FUTURE_SKEW_MILLIS = 2L * 60 * 1_000
    private const val SESSION_CLOCK_SKEW_MILLIS = 5_000L
    private const val MIN_LATITUDE_DEGREES = -90.0
    private const val MAX_LATITUDE_DEGREES = 90.0
    private const val MIN_LONGITUDE_DEGREES = -180.0
    private const val MAX_LONGITUDE_DEGREES = 180.0

    data class Validated(val capturedAt: Long)

    fun validate(location: LocationResult, sessionStartedAt: Long, receivedAt: Long): Validated? {
        if (
            !location.latitude.isFinite() ||
            location.latitude !in MIN_LATITUDE_DEGREES..MAX_LATITUDE_DEGREES
        ) return null
        if (
            !location.longitude.isFinite() ||
            location.longitude !in MIN_LONGITUDE_DEGREES..MAX_LONGITUDE_DEGREES
        ) return null

        val capturedAt = location.locationTime.takeIf { it > 0L } ?: receivedAt
        if (capturedAt < sessionStartedAt - SESSION_CLOCK_SKEW_MILLIS) return null
        if (capturedAt < receivedAt - MAX_SAMPLE_AGE_MILLIS) return null
        if (capturedAt > receivedAt + MAX_FUTURE_SKEW_MILLIS) return null
        return Validated(capturedAt)
    }
}
