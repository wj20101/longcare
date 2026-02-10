package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.provider.SystemLocationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefaultLocationFacadeCancellationTest {

    @Test
    fun `getCurrentLocation should rethrow cancellation from amap source`() = runTest {
        val amap = mockk<ContinuousAmapLocationManager>()
        val stateManager = mockk<LocationStateManager>()
        val systemProvider = mockk<SystemLocationProvider>()
        val keepAliveManager = mockk<LocationKeepAliveManager>()

        coEvery { stateManager.getValidLocation(any()) } returns null
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
}
