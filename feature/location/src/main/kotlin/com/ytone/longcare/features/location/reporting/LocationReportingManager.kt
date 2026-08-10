package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationUploadStatus
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.domain.location.LocationUploadQueueRepository
import com.ytone.longcare.features.location.manager.LocationStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 位置上报任务管理器。
 * 只关注“取定位并上报”这件事，不负责定位服务保活的具体实现。
 */
@Singleton
class LocationReportingManager @Inject constructor(
    private val locationFacade: LocationFacade,
    private val locationStateManager: LocationStateManager,
    private val locationRepository: LocationRepository,
    private val locationUploadQueueRepository: LocationUploadQueueRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentTrackingOrderKey = MutableStateFlow<OrderKey?>(null)
    val currentTrackingOrderKey: StateFlow<OrderKey?> = _currentTrackingOrderKey.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var reportingJob: Job? = null
    private var currentOwner: String? = null
    private val uploadMutex = Mutex()
    private var lastObservedLocation: LocationResult? = null
    private var lastObservedLocationTimeMs: Long? = null
    private var lastSampleBuglyReportTimeMs: Long = 0L
    private var lastJumpBuglyReportTimeMs: Long = 0L

    companion object {
        private const val MAX_UPLOAD_BATCH = 30
        private const val SUCCESS_RETENTION_MS = 24 * 60 * 60 * 1000L
        private const val SAMPLE_BUGLY_REPORT_INTERVAL_MS = 5 * 60 * 1000L
        private const val JUMP_BUGLY_REPORT_INTERVAL_MS = 60 * 1000L
        private const val STALE_LOCATION_MAX_AGE_MS = 2 * 60 * 1000L
        private const val SUSPICIOUS_JUMP_DISTANCE_M = 500.0
        private const val FORCE_REPORT_JUMP_DISTANCE_M = 1_500.0
        private const val SUSPICIOUS_SPEED_MPS = 30.0
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    fun startReporting(orderKey: OrderKey) {
        val sameTaskRunning = _isTracking.value &&
            _currentTrackingOrderKey.value?.orderId == orderKey.orderId &&
            reportingJob?.isActive == true
        if (sameTaskRunning) return

        stopReporting()

        _currentTrackingOrderKey.value = orderKey
        _isTracking.value = true
        locationStateManager.startTracking(orderKey)

        val owner = buildOwner(orderKey)
        currentOwner = owner
        locationFacade.acquireKeepAlive(owner)
        resetLocationDiagnostics()
        LocationEventTracker.trackEvent(
            LocationEventTracker.EventType.REPORTING_START,
            extras = mapOf("orderId" to orderKey.orderId)
        )

        reportingJob = scope.launch {
            try {
                flushUploadQueue()
                locationFacade.observeLocations().collect { location ->
                    val now = System.currentTimeMillis()
                    if (shouldSkipStaleLocation(orderKey.orderId, location, now)) {
                        return@collect
                    }
                    trackLocationDiagnostics(orderKey.orderId, location, now)
                    enqueueLocation(orderKey.orderId, location)
                    flushUploadQueue()
                }
            } catch (_: CancellationException) {
                logI("位置上报任务已取消")
            } catch (e: Exception) {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.REPORTING_TASK_ERROR,
                    throwable = e,
                    extras = mapOf("errorMsg" to e.message)
                )
            } finally {
                logI("位置上报任务结束")
            }
        }
    }

    fun stopReporting() {
        val wasTracking = _isTracking.value || reportingJob?.isActive == true
        val stoppedOrderId = _currentTrackingOrderKey.value?.orderId
        reportingJob?.cancel()
        reportingJob = null

        currentOwner?.let { owner ->
            locationFacade.releaseKeepAlive(owner)
        }
        currentOwner = null

        if (wasTracking) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.REPORTING_STOP,
                extras = mapOf("orderId" to stoppedOrderId)
            )
        }
        resetLocationDiagnostics()
        _isTracking.value = false
        _currentTrackingOrderKey.value = null
        locationStateManager.stopTracking()
    }

    fun forceStopReporting() {
        stopReporting()
    }

    private fun shouldSkipStaleLocation(orderId: Long, location: LocationResult, now: Long): Boolean {
        val locationTime = location.locationTime
        if (locationTime <= 0L) return false

        val ageMs = now - locationTime
        if (ageMs < 0L || ageMs <= STALE_LOCATION_MAX_AGE_MS) return false

        LocationEventTracker.trackLocationSample(
            eventType = LocationEventTracker.EventType.LOCATION_STALE_SKIPPED,
            orderId = orderId,
            location = location,
            extras = mapOf(
                "ageMs" to ageMs,
                "staleThresholdMs" to STALE_LOCATION_MAX_AGE_MS
            )
        )
        return true
    }

    private fun trackLocationDiagnostics(orderId: Long, location: LocationResult, now: Long) {
        val currentLocationTimeMs = location.locationTime.takeIf { it > 0L } ?: now
        maybeTrackSample(orderId, location, now)
        maybeTrackJump(orderId, location, currentLocationTimeMs, now)
        lastObservedLocation = location
        lastObservedLocationTimeMs = currentLocationTimeMs
    }

    private fun maybeTrackSample(orderId: Long, location: LocationResult, now: Long) {
        val shouldReport = lastSampleBuglyReportTimeMs == 0L ||
            now - lastSampleBuglyReportTimeMs >= SAMPLE_BUGLY_REPORT_INTERVAL_MS
        if (!shouldReport) return

        val reason = if (lastSampleBuglyReportTimeMs == 0L) "first" else "periodic"
        LocationEventTracker.trackLocationSample(
            eventType = LocationEventTracker.EventType.LOCATION_SAMPLE_RECORDED,
            orderId = orderId,
            location = location,
            extras = mapOf("sampleReason" to reason)
        )
        lastSampleBuglyReportTimeMs = now
    }

    private fun maybeTrackJump(
        orderId: Long,
        location: LocationResult,
        currentLocationTimeMs: Long,
        now: Long
    ) {
        val previous = lastObservedLocation ?: return
        val previousTimeMs = lastObservedLocationTimeMs ?: return
        val elapsedSeconds = (currentLocationTimeMs - previousTimeMs) / 1000.0
        if (elapsedSeconds <= 0.0) return

        val distanceMeters = distanceMeters(
            startLatitude = previous.latitude,
            startLongitude = previous.longitude,
            endLatitude = location.latitude,
            endLongitude = location.longitude
        )
        val speedMps = distanceMeters / elapsedSeconds
        val suspicious = distanceMeters >= FORCE_REPORT_JUMP_DISTANCE_M ||
            (distanceMeters >= SUSPICIOUS_JUMP_DISTANCE_M && speedMps >= SUSPICIOUS_SPEED_MPS)
        val throttled = now - lastJumpBuglyReportTimeMs < JUMP_BUGLY_REPORT_INTERVAL_MS
        if (!suspicious || throttled) return

        LocationEventTracker.trackLocationSample(
            eventType = LocationEventTracker.EventType.LOCATION_JUMP_DETECTED,
            orderId = orderId,
            location = location,
            extras = mapOf(
                "previousLatitude" to previous.latitude.formatCoordinate(),
                "previousLongitude" to previous.longitude.formatCoordinate(),
                "previousProvider" to previous.provider,
                "previousAccuracy" to previous.accuracy,
                "previousCoordType" to previous.coordType,
                "previousLocationType" to previous.locationType,
                "previousTrustedLevel" to previous.trustedLevel,
                "previousLocationTime" to previous.locationTime,
                "distanceMeters" to distanceMeters.formatOneDecimal(),
                "elapsedSeconds" to elapsedSeconds.formatOneDecimal(),
                "speedMps" to speedMps.formatOneDecimal()
            )
        )
        lastJumpBuglyReportTimeMs = now
    }

    private fun resetLocationDiagnostics() {
        lastObservedLocation = null
        lastObservedLocationTimeMs = null
        lastSampleBuglyReportTimeMs = 0L
        lastJumpBuglyReportTimeMs = 0L
    }

    private suspend fun enqueueLocation(orderId: Long, location: LocationResult) {
        try {
            locationUploadQueueRepository.insert(
                OrderLocationEntity(
                    orderId = orderId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    provider = location.provider,
                    coordType = location.coordType,
                    locationType = location.locationType,
                    trustedLevel = location.trustedLevel,
                    locationTime = location.locationTime,
                    uploadStatus = LocationUploadStatus.PENDING.value,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.QUEUE_WRITE_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
        }
    }

    private suspend fun flushUploadQueue() {
        uploadMutex.withLock {
            val queue = locationUploadQueueRepository.getUploadQueue(
                statuses = listOf(
                    LocationUploadStatus.PENDING.value,
                    LocationUploadStatus.FAILED.value
                ),
                limit = MAX_UPLOAD_BATCH
            )

            queue.forEach { pending ->
                uploadSingle(pending)
            }

            cleanupOldSuccess()
        }
    }

    private suspend fun uploadSingle(pending: OrderLocationEntity) {
        try {
            when (val apiResult = locationRepository.addPosition(
                orderId = pending.orderId,
                latitude = pending.latitude,
                longitude = pending.longitude
            )) {
                is ApiResult.Success -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.SUCCESS.value)
                    logI("位置上报成功 (orderId=${pending.orderId}, id=${pending.id})")
                }
                is ApiResult.Failure -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.API_UPLOAD_BUSINESS_ERROR,
                        extras = mapOf("pendingId" to pending.id, "errorMsg" to apiResult.message)
                    )
                }
                is ApiResult.Exception -> {
                    locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.API_UPLOAD_NETWORK_ERROR,
                        throwable = apiResult.exception,
                        extras = mapOf("pendingId" to pending.id)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
            try {
                locationUploadQueueRepository.updateStatus(pending.id, LocationUploadStatus.FAILED.value)
            } catch (statusException: CancellationException) {
                throw statusException
            } catch (statusException: Exception) {
                LocationEventTracker.trackError(
                    LocationEventTracker.EventType.API_UPLOAD_FATAL_ERROR,
                    throwable = statusException,
                    extras = mapOf(
                        "pendingId" to pending.id,
                        "phase" to "mark_failed",
                    ),
                )
            }
        }
    }

    private suspend fun cleanupOldSuccess() {
        try {
            val deleted = locationUploadQueueRepository.deleteByStatusBefore(
                status = LocationUploadStatus.SUCCESS.value,
                beforeTime = System.currentTimeMillis() - SUCCESS_RETENTION_MS
            )
            if (deleted > 0) {
                logI("清理历史成功定位记录: $deleted 条")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.QUEUE_CLEANUP_ERROR,
                throwable = e,
                extras = mapOf("errorMsg" to e.message)
            )
        }
    }

    private fun buildOwner(orderKey: OrderKey): String {
        return "location_report_${orderKey.orderId}"
    }

    private fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val startLatRad = Math.toRadians(startLatitude)
        val endLatRad = Math.toRadians(endLatitude)
        val deltaLatRad = Math.toRadians(endLatitude - startLatitude)
        val deltaLonRad = Math.toRadians(endLongitude - startLongitude)
        val haversine = sin(deltaLatRad / 2).pow(2) +
            cos(startLatRad) * cos(endLatRad) * sin(deltaLonRad / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    private fun Double.formatCoordinate(): String {
        return String.format(Locale.US, "%.5f", this)
    }

    private fun Double.formatOneDecimal(): String {
        return String.format(Locale.US, "%.1f", this)
    }
}
