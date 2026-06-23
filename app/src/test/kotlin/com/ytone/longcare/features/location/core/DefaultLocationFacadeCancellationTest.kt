package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.model.LocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultLocationFacadeCancellationTest {

    @Test
    fun `getCurrentLocation should return business cache before waiting for providers`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()
        val cachedLocation = LocationResult(
            latitude = 30.0,
            longitude = 120.0,
            provider = "cached",
            accuracy = 10f
        )

        every { stateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS) } returns cachedLocation

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            locationKeepAliveManager = keepAliveManager
        )

        val result = facade.getCurrentLocation()

        assertSame(cachedLocation, result)
        verify(exactly = 1) { stateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS) }
        coVerify(exactly = 0) { amap.getCurrentLocation(any()) }
    }

    @Test
    fun `getCurrentLocation should return null when amap returns null`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        every { stateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS) } returns null
        coEvery { amap.getCurrentLocation(any()) } returns null

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            locationKeepAliveManager = keepAliveManager
        )

        val result = facade.getCurrentLocation(timeoutMs = 1_000L)

        assertNull(result)
    }

    @Test
    fun `getCurrentLocation should rethrow cancellation from amap source`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        every { stateManager.getValidLocation(any()) } returns null
        coEvery { amap.getCurrentLocation(any()) } throws CancellationException("cancelled")

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            locationKeepAliveManager = keepAliveManager
        )

        val cancellation = try {
            facade.getCurrentLocation(timeoutMs = 1000)
            null
        } catch (e: CancellationException) {
            e
        }

        assertNotNull(cancellation)
    }

    @Test
    fun `getFreshLocation should bypass business cache and request fresh amap location`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>(relaxed = true)
        val keepAliveManager = mockk<LocationKeepAliveManager>()
        val cachedLocation = LocationResult(
            latitude = 30.0,
            longitude = 120.0,
            provider = "cached",
            accuracy = 10f
        )
        val freshLocation = LocationResult(
            latitude = 31.2304,
            longitude = 121.4737,
            provider = "amap_fresh",
            accuracy = 8f
        )

        every { stateManager.getValidLocation(any()) } returns cachedLocation
        coEvery { amap.getFreshLocation(any()) } returns freshLocation

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            locationKeepAliveManager = keepAliveManager
        )

        val result = facade.getFreshLocation(timeoutMs = 4_000L)

        assertSame(freshLocation, result)
        verify(exactly = 0) { stateManager.getValidLocation(any()) }
        coVerify(exactly = 1) { amap.getFreshLocation(8_000L) }
    }

    @Test
    fun `getFreshLocation should rethrow cancellation from amap source`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>(relaxed = true)
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        coEvery { amap.getFreshLocation(any()) } throws CancellationException("fresh cancelled")

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            locationKeepAliveManager = keepAliveManager
        )

        val cancellation = try {
            facade.getFreshLocation(timeoutMs = 10_000L)
            null
        } catch (e: CancellationException) {
            e
        }

        assertNotNull(cancellation)
        verify(exactly = 0) { stateManager.getValidLocation(any()) }
    }
}
