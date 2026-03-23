package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LocationTrackingManagerTest {

    private val locationFacade = mockk<LocationFacade>(relaxed = true)
    private val locationReportingManager = mockk<LocationReportingManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("com.ytone.longcare.common.utils.LogExtKt")
        every { any<Any>().logI(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.ytone.longcare.common.utils.LogExtKt")
    }

    @Test
    fun `forceStopTracking should release ui keep alive when session is active`() {
        val manager = createManager()

        manager.startLocationSession()
        manager.forceStopTracking()

        verify(exactly = 1) { locationReportingManager.forceStopReporting() }
        verify(exactly = 1) { locationFacade.acquireKeepAlive("location_ui_session") }
        verify(exactly = 1) { locationFacade.releaseKeepAlive("location_ui_session") }
    }

    @Test
    fun `forceStopTracking should still release ui keep alive when session is not active`() {
        val manager = createManager()

        manager.forceStopTracking()

        verify(exactly = 1) { locationReportingManager.forceStopReporting() }
        verify(exactly = 1) { locationFacade.releaseKeepAlive("location_ui_session") }
    }

    private fun createManager(): LocationTrackingManager {
        return LocationTrackingManager(
            locationFacade = locationFacade,
            locationReportingManager = locationReportingManager
        )
    }
}
