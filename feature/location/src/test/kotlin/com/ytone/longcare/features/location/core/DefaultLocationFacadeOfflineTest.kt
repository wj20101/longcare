package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationSampleStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultLocationFacadeOfflineTest {
    @Test
    fun `offline amap unavailable result is recoverable and not a first party mock success`() =
        runTest {
            val amapManager = mockk<ContinuousAmapLocationManager>()
            val sampleStore = mockk<LocationSampleStore>()
            every { sampleStore.getValidLocation(any()) } returns null
            coEvery { amapManager.getCurrentLocation(any()) } returns null
            val facade =
                DefaultLocationFacade(
                    continuousAmapLocationManager = amapManager,
                    locationSampleStore = sampleStore,
                    locationKeepAliveManager = mockk(relaxed = true),
                )

            val result = facade.getCurrentLocation(timeoutMs = 5_000)

            assertNull(result)
        }
}
