package com.ytone.longcare.features.location.tracker

import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.model.LocationResult
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationDiagnosticsRegressionTest {

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.postCaughtException(any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `routine lifecycle event must not be reported as a caught exception`() {
        LocationEventTracker.trackEvent(
            eventType = LocationEventTracker.EventType.REPORTING_START,
            extras = mapOf("orderId" to 42L),
        )

        verify(exactly = 0) { CrashReportGateway.postCaughtException(any()) }
    }

    @Test
    fun `remote diagnostic payload must not contain precise coordinates`() {
        val result = buildLocationExtras(
            orderId = 42L,
            location = LocationResult(
                latitude = 31.23041,
                longitude = 121.47370,
                provider = "amap_continuous",
                accuracy = 8f,
                coordType = "GCJ02",
                locationType = 5,
                trustedLevel = 2,
                locationTime = 123456789L,
            ),
        )

        val latitude = result["latitude"]?.toString()
        val longitude = result["longitude"]?.toString()
        assertTrue(
            "Diagnostics should omit coordinates or reduce them to coarse precision",
            latitude == null || latitude.decimalPlaces() <= MAX_DIAGNOSTIC_DECIMAL_PLACES,
        )
        assertTrue(
            "Diagnostics should omit coordinates or reduce them to coarse precision",
            longitude == null || longitude.decimalPlaces() <= MAX_DIAGNOSTIC_DECIMAL_PLACES,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildLocationExtras(
        orderId: Long,
        location: LocationResult,
    ): Map<String, Any?> {
        val method = LocationEventTracker::class.java.getDeclaredMethod(
            "buildLocationExtras",
            Long::class.javaPrimitiveType,
            LocationResult::class.java,
            Map::class.java,
        )
        method.isAccessible = true
        return method.invoke(LocationEventTracker, orderId, location, emptyMap<String, Any?>())
            as Map<String, Any?>
    }

    private fun String.decimalPlaces(): Int = substringAfter('.', missingDelimiterValue = "").length

    private companion object {
        const val MAX_DIAGNOSTIC_DECIMAL_PLACES = 3
    }
}
