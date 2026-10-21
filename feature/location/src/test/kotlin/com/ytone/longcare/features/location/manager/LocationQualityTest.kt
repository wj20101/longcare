package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.domain.location.LocationQuality.Rejection
import com.ytone.longcare.model.LocationResult
import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    private val sample = LocationResult(31.2, 121.4, "network", 8f, "GCJ02", 5, 1,
        locationTime = 100_000, receivedAt = 100_000, receivedElapsedRealtime = 1_000)

    private fun reason(location: LocationResult) = LocationQuality.rejection(location, 100_000, 1_000)

    @Test fun `metadata not displacement determines validity`() {
        assertNull(reason(sample))
        assertNull(reason(sample.copy(latitude = -80.0, longitude = -170.0)))
        assertNull(reason(sample.copy(accuracy = 10_000f)))
        listOf(1, 2, 5, 6, 7, 12).forEach { assertNull(reason(sample.copy(locationType = it))) }
    }

    @Test fun `invalid coordinates and missing accuracy are rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -90.1, 90.1).forEach {
            assertEquals(Rejection.COORDINATES, reason(sample.copy(latitude = it)))
        }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, -180.1, 180.1).forEach {
            assertEquals(Rejection.COORDINATES, reason(sample.copy(longitude = it)))
        }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(Rejection.ACCURACY, reason(sample.copy(accuracy = it)))
        }
    }

    @Test fun `cache mock coarse unknown source trust and coordinate frame fail`() {
        listOf(0, 3, 4, 8, 9, 10, 11, 99).forEach {
            assertEquals(Rejection.SOURCE, reason(sample.copy(locationType = it)))
        }
        listOf(0, 2, 3, 4).forEach { assertEquals(Rejection.TRUST, reason(sample.copy(trustedLevel = it))) }
        assertEquals(Rejection.SOURCE, reason(sample.copy(isLastLocation = true)))
        assertEquals(Rejection.MOCK, reason(sample.copy(isMock = true)))
        listOf("", "WGS84").forEach { assertEquals(Rejection.COORDINATE_TYPE, reason(sample.copy(coordType = it))) }
    }

    @Test fun `missing future and pre session timestamps never become current`() {
        assertEquals(Rejection.TIME, reason(sample.copy(locationTime = 0)))
        assertEquals(Rejection.TIME, reason(sample.copy(locationTime = 100_001)))
        assertEquals(Rejection.TIME, reason(sample.copy(receivedAt = 0)))
        assertEquals(Rejection.ORDER, LocationQuality.rejection(sample, 100_000, 1_000, notBeforeElapsedRealtime = 1_001))
    }

    @Test fun `fifteen second boundary includes age before receipt and time waiting`() {
        assertNull(LocationQuality.rejection(sample, 115_000, 16_000))
        assertEquals(Rejection.STALE, LocationQuality.rejection(sample, 115_001, 16_001))
        assertNull(reason(sample.copy(locationTime = 85_000)))
        assertEquals(Rejection.STALE, reason(sample.copy(locationTime = 84_999)))
        assertEquals(Rejection.STALE, LocationQuality.rejection(sample.copy(locationTime = 99_000), 114_001, 15_001))
    }

    @Test fun `wall clock changes cannot renew an old sample`() {
        assertEquals(Rejection.STALE, LocationQuality.rejection(sample, 100_000, 16_001))
        assertEquals(Rejection.TIME, LocationQuality.rejection(sample, 99_999, 1_000))
        assertEquals(Rejection.TIME, LocationQuality.rejection(sample, 100_000, 999))
    }

    @Test fun `source monotonic age includes time before receipt and waiting after receipt`() {
        val reused = sample.copy(receivedElapsedRealtime = 100_000, sourceElapsedRealtime = 85_000)
        assertNull(LocationQuality.rejection(reused, 100_000, 100_000))
        assertEquals(Rejection.STALE, LocationQuality.rejection(reused, 100_001, 100_001))

        val waiting = reused.copy(sourceElapsedRealtime = 86_000)
        assertNull(LocationQuality.rejection(waiting, 101_000, 101_000))
        assertEquals(Rejection.STALE, LocationQuality.rejection(waiting, 101_001, 101_001))
        assertEquals(Rejection.STALE, LocationQuality.rejection(
            reused.copy(sourceElapsedRealtime = 84_999), 100_000, 100_000,
        ))
    }

    @Test fun `refreshed SDK wall time cannot renew a reporting sample with an old source tick`() {
        val reused = sample.copy(
            locationTime = 200_000, receivedAt = 200_000,
            receivedElapsedRealtime = 100_000, sourceElapsedRealtime = 40_000,
        )
        assertNull(ServiceOrderLocationQuality.rejection(reused, 200_000, 100_000,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS))
        assertEquals(Rejection.STALE, ServiceOrderLocationQuality.rejection(
            reused.copy(locationTime = 200_001, receivedAt = 200_001, receivedElapsedRealtime = 100_001),
            200_001, 100_001, maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        ))
        assertEquals(Rejection.STALE, ServiceOrderLocationQuality.rejection(reused, 200_000, 100_001,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS))
    }

    @Test fun `source tick beyond receipt or negative is invalid even without an age limit`() {
        listOf(-1L, 1_001L).forEach { invalid ->
            assertEquals(Rejection.TIME, ServiceOrderLocationQuality.rejection(
                sample.copy(sourceElapsedRealtime = invalid), 100_000, 1_000,
            ))
        }
        assertNull(reason(sample.copy(sourceElapsedRealtime = 1_000)))
    }

    @Test fun `action cutoff uses source age when SDK wall time has been refreshed`() {
        val reused = sample.copy(receivedElapsedRealtime = 100_000, sourceElapsedRealtime = 97_000)
        assertNull(ServiceOrderLocationQuality.rejection(reused, 100_000, 100_000, notBeforeElapsedRealtime = 97_000))
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            reused.copy(sourceElapsedRealtime = 96_999), 100_000, 100_000, notBeforeElapsedRealtime = 97_000,
        ))
        // Removing the service action's age limit must not remove its order isolation.
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            reused.copy(sourceElapsedRealtime = 1), 100_000, 100_000, notBeforeElapsedRealtime = 97_000,
        ))
    }

    @Test fun `forward wall correction cannot admit a source tick from before the action`() {
        // 动作开始于 wall=100000、elapsed=100000；回调前墙上时间向前跳变。
        val received = sample.copy(
            locationTime = 200_000, receivedAt = 200_000,
            receivedElapsedRealtime = 110_000, sourceElapsedRealtime = 90_000,
        )
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            received, 200_000, 110_000, notBeforeElapsedRealtime = 97_000,
        ))
        // 仍允许动作前 3 秒的预热边界；缺失源时钟时保持原来的兼容路径。
        assertNull(ServiceOrderLocationQuality.rejection(
            received.copy(sourceElapsedRealtime = 97_000), 200_000, 110_000,
            notBeforeElapsedRealtime = 97_000,
        ))
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            received.copy(sourceElapsedRealtime = 96_999), 200_000, 110_000,
            notBeforeElapsedRealtime = 97_000,
        ))
        assertNull(ServiceOrderLocationQuality.rejection(
            received.copy(sourceElapsedRealtime = 0), 200_000, 110_000,
            notBeforeElapsedRealtime = 97_000,
        ))
    }

    @Test fun `missing source tick preserves existing wall time age and cutoff behavior`() {
        assertEquals(0L, sample.sourceElapsedRealtime)
        assertNull(reason(sample))
        assertEquals(Rejection.STALE, reason(sample.copy(locationTime = 84_999)))
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            sample.copy(locationTime = 96_999, receivedElapsedRealtime = 100_000), 100_000, 100_000,
            notBeforeElapsedRealtime = 97_000,
        ))
        assertNull(ServiceOrderLocationQuality.rejection(sample, 160_001, 61_001))
    }

    @Test fun `fresh GPS source time accepts independent wall clocks without modifying coordinates or time`() {
        val gps = sample.copy(locationType = 1, receivedElapsedRealtime = 100_000, sourceElapsedRealtime = 100_000)
        listOf(40_000L, 100_001L, 105_000L).forEach { fixTime ->
            val fix = gps.copy(locationTime = fixTime)
            assertNull(LocationQuality.rejection(fix, 100_000, 100_000))
            assertNull(ServiceOrderLocationQuality.rejection(fix, 100_000, 100_000,
                notBeforeElapsedRealtime = 97_000))
            // 校时只改变墙钟，既不能更新旧源点，也不能拒绝新的源点。
            assertNull(LocationQuality.rejection(fix, 90_000, 100_000))
            assertEquals(Rejection.STALE, LocationQuality.rejection(fix, 90_000, 115_001))
            assertEquals(fixTime, fix.locationTime)
            assertEquals(sample.longitude, fix.longitude, 0.0)
        }
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            gps.copy(sourceElapsedRealtime = 96_999), 100_000, 100_000,
            notBeforeElapsedRealtime = 97_000,
        ))
    }

    @Test fun `zero action cutoff rejects pre boot fallback points and keeps the warm boundary`() {
        // 动作发生于开机 1 秒，3 秒预热窗口下界为 0，不能把 0 当作未设置。
        val old = sample.copy(locationTime = 90_000, sourceElapsedRealtime = 0)
        assertNull(ServiceOrderLocationQuality.rejection(old, 100_000, 1_000))
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            old, 100_000, 1_000, notBeforeElapsedRealtime = 0,
        ))
        val warm = sample.copy(locationTime = 99_000, sourceElapsedRealtime = 0)
        assertEquals(0L, LocationQuality.sampleElapsedRealtime(warm))
        assertNull(ServiceOrderLocationQuality.rejection(warm, 100_000, 1_000, notBeforeElapsedRealtime = 0))
        assertEquals(Rejection.ORDER, ServiceOrderLocationQuality.rejection(
            warm.copy(locationTime = 98_999), 100_000, 1_000, notBeforeElapsedRealtime = 0,
        ))
    }

    @Test fun `time issues distinguish missing metadata future source clocks and fallback wall clocks`() {
        fun issue(fix: LocationResult, now: Long = 100_000L, elapsed: Long = 1_000L) =
            LocationQuality.timeIssue(fix, now, elapsed)
        assertEquals(LocationQuality.TimeIssue.FIX_MISSING, issue(sample.copy(locationTime = 0)))
        assertEquals(LocationQuality.TimeIssue.RECEIPT_MISSING, issue(sample.copy(receivedAt = 0)))
        assertEquals(LocationQuality.TimeIssue.RECEIPT_ELAPSED_INVALID, issue(sample.copy(receivedElapsedRealtime = -1)))
        assertEquals(LocationQuality.TimeIssue.SOURCE_INVALID, issue(sample.copy(sourceElapsedRealtime = -1)))
        assertEquals(LocationQuality.TimeIssue.SOURCE_AFTER_RECEIPT, issue(sample.copy(sourceElapsedRealtime = 1_001)))
        assertEquals(LocationQuality.TimeIssue.ELAPSED_BEFORE_RECEIPT, issue(sample, elapsed = 999))
        assertEquals(LocationQuality.TimeIssue.FIX_AFTER_RECEIPT, issue(sample.copy(locationTime = 100_001)))
        assertEquals(LocationQuality.TimeIssue.WALL_BEFORE_RECEIPT, issue(sample, now = 99_999))
        assertNull(issue(sample.copy(sourceElapsedRealtime = 1_000, locationTime = 105_000), now = 90_000))
    }
}
