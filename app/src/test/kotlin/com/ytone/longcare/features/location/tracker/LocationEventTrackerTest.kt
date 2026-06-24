package com.ytone.longcare.features.location.tracker

import com.ytone.longcare.model.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationEventTrackerTest {

    @Test
    fun `buildLocationExtras keeps canonical location fields ahead of caller extras`() {
        val location = LocationResult(
            latitude = 31.23041,
            longitude = 121.47370,
            provider = "amap_fresh",
            accuracy = 12.5f,
            coordType = "gcj02",
            locationType = 5,
            trustedLevel = 3,
            locationTime = 123456789L
        )

        val extras = mapOf(
            "orderId" to 99L,
            "latitude" to "0.00000",
            "longitude" to "0.00000",
            "provider" to "spoofed",
            "accuracy" to -1f,
            "coordType" to "fake",
            "locationType" to -1,
            "trustedLevel" to -1,
            "locationTime" to 0L,
            "customFlag" to "kept"
        )

        val result = buildLocationExtras(orderId = 42L, location = location, extras = extras)

        assertEquals(42L, result["orderId"])
        assertEquals("31.23041", result["latitude"])
        assertEquals("121.47370", result["longitude"])
        assertEquals("amap_fresh", result["provider"])
        assertEquals(12.5f, result["accuracy"])
        assertEquals("gcj02", result["coordType"])
        assertEquals(5, result["locationType"])
        assertEquals(3, result["trustedLevel"])
        assertEquals(123456789L, result["locationTime"])
        assertEquals("kept", result["customFlag"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildLocationExtras(
        orderId: Long,
        location: LocationResult,
        extras: Map<String, Any?>
    ): Map<String, Any?> {
        val method = LocationEventTracker::class.java.getDeclaredMethod(
            "buildLocationExtras",
            Long::class.javaPrimitiveType,
            LocationResult::class.java,
            Map::class.java
        )
        method.isAccessible = true
        return method.invoke(LocationEventTracker, orderId, location, extras) as Map<String, Any?>
    }
}
