package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationQuality
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
        assertEquals(Rejection.ORDER, LocationQuality.rejection(sample, 100_000, 1_000, notBefore = 100_001))
        assertEquals(Rejection.ORDER, LocationQuality.rejection(sample, 100_000, 1_000, previousSampleTime = 100_000))
        assertNull(LocationQuality.rejection(sample, 100_000, 1_000, previousSampleTime = 99_999))
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
}
