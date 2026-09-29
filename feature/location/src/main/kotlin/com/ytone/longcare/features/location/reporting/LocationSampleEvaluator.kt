package com.ytone.longcare.features.location.reporting

import android.os.SystemClock
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

/** 仅保留会话内采样时序；不存在前一点坐标、距离或速度判断。 */
internal class LocationSampleEvaluator(
    private val orderId: Long,
    private val sessionStartedAt: Long,
    private val clock: LocationClock,
) {
    private var lastSampleTime = 0L
    private var lastDiagnosticElapsed: Long? = null

    fun shouldUpload(location: LocationResult): Boolean {
        val now = clock.currentTimeMillis()
        val elapsed = clock.elapsedRealtime()
        val rejection = LocationQuality.rejection(
            location, now, elapsed, notBefore = sessionStartedAt, previousSampleTime = lastSampleTime,
        )
        if (rejection != null) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.LOCATION_INVALID_SKIPPED,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.SAMPLE_REASON to rejection.name,
                ),
            )
            return false
        }
        lastSampleTime = location.locationTime
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
