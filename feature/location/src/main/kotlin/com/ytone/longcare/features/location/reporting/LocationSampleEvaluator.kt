package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Preserves sample validation and diagnostics inside the Service-owned session. */
internal class LocationSampleEvaluator(
    private val orderId: Long,
    private val sessionStartedAt: Long,
    private val clock: LocationClock,
) {
    private var lastObservedLocation: LocationResult? = null
    private var lastObservedLocationTimeMs: Long? = null
    private var lastSampleDiagnosticTimeMs: Long = 0L
    private var lastJumpDiagnosticTimeMs: Long = 0L

    fun shouldUpload(location: LocationResult): Boolean {
        val receivedAt = clock.currentTimeMillis()
        val validated = LocationSampleValidator.validate(location, sessionStartedAt, receivedAt)
        if (validated == null) {
            LocationEventTracker.trackEvent(
                LocationEventTracker.EventType.LOCATION_INVALID_SKIPPED,
                extras = mapOf(
                    LocationEventTracker.Attribute.ORDER_ID to orderId,
                    LocationEventTracker.Attribute.PROVIDER to location.provider,
                ),
            )
            return false
        }

        maybeTrackSample(location, receivedAt)
        maybeTrackJump(location, validated.capturedAt, receivedAt)
        lastObservedLocation = location
        lastObservedLocationTimeMs = validated.capturedAt
        return true
    }

    private fun maybeTrackSample(location: LocationResult, now: Long) {
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

    private fun maybeTrackJump(location: LocationResult, capturedAt: Long, now: Long) {
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

    private fun distanceMeters(start: LocationResult, end: LocationResult): Double {
        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val haversine = sin(deltaLat / 2).pow(2) +
            cos(startLat) * cos(endLat) * sin(deltaLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

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
