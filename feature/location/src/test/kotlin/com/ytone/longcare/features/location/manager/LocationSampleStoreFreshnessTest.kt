package com.ytone.longcare.features.location.manager

import com.ytone.longcare.model.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationSampleStoreFreshnessTest {
    @Test
    fun `replayed location older than cache policy remains expired`() {
        val store = LocationSampleStore()
        val oldSdkTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L
        store.record(location(locationTime = oldSdkTimestamp))

        assertNull(store.getValidLocation(maxAgeMs = 5 * 60 * 1000L))
    }

    @Test
    fun `cached location preserves the SDK sample`() {
        val store = LocationSampleStore()
        val expected = location(locationTime = System.currentTimeMillis() - 60_000L)
        store.record(expected)

        assertEquals(expected, store.getValidLocation(maxAgeMs = 5 * 60 * 1000L))
    }

    @Test
    fun `location timestamp far in the future is rejected`() {
        val store = LocationSampleStore()
        val futureTimestamp = System.currentTimeMillis() + 10 * 60 * 1000L
        store.record(location(locationTime = futureTimestamp))

        assertNull(store.getValidLocation(maxAgeMs = 5 * 60 * 1000L))
    }

    private fun location(locationTime: Long): LocationResult = LocationResult(
        latitude = 31.23041,
        longitude = 121.47370,
        provider = "amap_continuous",
        accuracy = 8f,
        coordType = "GCJ02",
        locationType = 5,
        trustedLevel = 2,
        locationTime = locationTime,
    )
}
