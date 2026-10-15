package com.ytone.longcare.features.location.reporting

import android.os.SystemClock
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

/** 仅保留会话内采样时序；不存在前一点坐标、距离或速度判断。 */
internal class LocationSampleEvaluator(
    private val orderId: Long,
    private val sessionStartedAt: Long,
    private val clock: LocationClock,
    private val minimumUploadIntervalMs: Long = 30_000L,
) {
    private var lastSampleTime = 0L
    private var lastDiagnosticElapsed: Long? = null
    private var lastAttemptElapsed: Long? = null

    fun shouldUpload(location: LocationResult): Boolean {
        val now = clock.currentTimeMillis()
        val elapsed = clock.elapsedRealtime()
        if (lastAttemptElapsed?.let { elapsed - it < minimumUploadIntervalMs } == true) return false
        val rejection = ServiceOrderLocationQuality.rejection(
            location, now, elapsed, notBefore = sessionStartedAt, previousSampleTime = lastSampleTime,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        )
        if (rejection != null) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.LOCATION_INVALID_SKIPPED,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.SAMPLE_REASON to rejection.name,
                    LocationEventTracker.Attribute.ACCURACY to location.accuracy.takeIf { it.isFinite() },
                    LocationEventTracker.Attribute.LOCATION_TYPE to location.locationType,
                    "accuracyBand" to ServiceOrderLocationQuality.accuracyBand(location.accuracy).name,
                    "requestId" to location.requestId,
                ),
            )
            return false
        }
        lastSampleTime = location.locationTime
        lastAttemptElapsed = elapsed
        if (lastDiagnosticElapsed == null || elapsed - requireNotNull(lastDiagnosticElapsed) >= 300_000L) {
            LocationEventTracker.trackLocationSample(
                LocationEventTracker.EventType.LOCATION_SAMPLE_RECORDED,
                orderId,
                location,
            )
            lastDiagnosticElapsed = elapsed
        }
        return true
    }
}

@Singleton
class LocationClock @Inject constructor() {
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
