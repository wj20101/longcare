package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.provider.SystemLocationProvider
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.model.LocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
        val systemProvider = mockk<SystemLocationProvider>()
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
            systemLocationProvider = systemProvider,
            locationKeepAliveManager = keepAliveManager
        )

        val result = facade.getCurrentLocation()

        assertSame(cachedLocation, result)
        verify(exactly = 1) { stateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS) }
        coVerify(exactly = 0) { amap.getCurrentLocation(any()) }
        coVerify(exactly = 0) { systemProvider.getCurrentLocation() }
    }

    @Test
    fun `getCurrentLocation should timeout slow system fallback`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val systemProvider = mockk<SystemLocationProvider>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        every { stateManager.getValidLocation(LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS) } returns null
        coEvery { amap.getCurrentLocation(any()) } returns null
        coEvery {
            systemProvider.getCurrentLocation()
        } coAnswers {
            delay(5_000L)
            LocationResult(
                latitude = 30.0,
                longitude = 120.0,
                provider = "system",
                accuracy = 10f
            )
        }

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            systemLocationProvider = systemProvider,
            locationKeepAliveManager = keepAliveManager
        )

        val result = facade.getCurrentLocation(timeoutMs = 1_000L)

        assertNull(result)
    }

    @Test
    fun `getCurrentLocation should rethrow cancellation from amap source`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val systemProvider = mockk<SystemLocationProvider>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        every { stateManager.getValidLocation(any()) } returns null
        coEvery { amap.getCurrentLocation(any()) } throws CancellationException("cancelled")

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            systemLocationProvider = systemProvider,
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
    fun `getCurrentLocation should rethrow cancellation from system provider`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val systemProvider = mockk<SystemLocationProvider>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        every { stateManager.getValidLocation(any()) } returns null
        coEvery { amap.getCurrentLocation(any()) } returns null
        coEvery { systemProvider.getCurrentLocation() } throws CancellationException("cancelled")

        val facade = DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            systemLocationProvider = systemProvider,
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
}
