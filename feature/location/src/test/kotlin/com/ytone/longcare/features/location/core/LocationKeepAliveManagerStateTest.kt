package com.ytone.longcare.features.location.core

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationKeepAliveManagerStateTest {
    private val serviceController = mockk<LocationForegroundServiceController>()
    private var lastGeneration = 0L

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        every { serviceController.start(any(), any(), any()) } answers { lastGeneration = secondArg() }
        every { serviceController.stop() } returns true
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackError(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `duplicate owner is idempotent after service confirmation`() {
        val manager = LocationKeepAliveManager(serviceController)

        manager.acquireOrderTracking("order", 1)
        val generation = lastGeneration
        assertTrue(manager.onServiceStarted(generation))
        manager.acquireOrderTracking("order", 1)

        assertTrue(manager.hasAcceptedStart())
        verify(exactly = 1) { serviceController.start("order", any(), 1) }
    }

    @Test
    fun `order tracking forwards order identity to foreground service`() {
        val manager = LocationKeepAliveManager(serviceController)

        manager.acquireOrderTracking("location_report_218490", 218_490L)

        verify(exactly = 1) {
            serviceController.start("location_report_218490", any(), 218_490L)
        }
    }

    @Test
    fun `old generation stop cannot overwrite a newer start`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquireOrderTracking("first", 1)
        val first = lastGeneration
        manager.release("first")
        manager.acquireOrderTracking("second", 1)
        val second = lastGeneration

        manager.onServiceStopped(first)

        assertTrue(manager.hasNewerRequestThan("first", first))
        assertTrue(!manager.hasNewerRequestThan("second", second))
        manager.acquireOrderTracking("second", 1)
        verify(exactly = 1) { serviceController.start("second", second, 1) }
    }

    @Test
    fun `old service state response cannot release newer order owner`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquireOrderTracking("location_report_1", 1L)
        val first = lastGeneration
        manager.release("location_report_1")
        manager.acquireOrderTracking("location_report_2", 2L)
        val second = lastGeneration

        manager.releaseFromService("location_report_1", first)

        assertTrue(manager.onServiceStarted(second))
        verify(exactly = 1) { serviceController.stop() }
    }

    @Test
    fun `stale service start is rejected before SDK collection`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquireOrderTracking("first", 1)
        val first = lastGeneration
        manager.release("first")
        manager.acquireOrderTracking("second", 1)
        val second = lastGeneration

        assertTrue(!manager.onServiceStarted(first))
        assertTrue(manager.onServiceStarted(second))
        manager.acquireOrderTracking("second", 1)
        verify(exactly = 1) { serviceController.start("second", second, 1) }
    }

    @Test
    fun `visible retry restarts an unexpectedly destroyed active service`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquireOrderTracking("order", 1)
        val first = lastGeneration
        manager.onServiceStarted(first)
        manager.onServiceStopped(first)

        assertTrue(!manager.hasAcceptedStart())
        manager.acquireOrderTracking("order", 1)

        assertEquals(first + 1, lastGeneration)
        verify(exactly = 2) { serviceController.start("order", any(), 1) }
    }

    @Test
    fun `a replaced owner cannot stop the single current owner`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquireOrderTracking("first", 1)
        manager.acquireOrderTracking("second", 2)

        manager.release("first")

        assertTrue(manager.hasAcceptedStart())
        verify(exactly = 0) { serviceController.stop() }
        manager.release("second")
        assertTrue(!manager.hasAcceptedStart())
        verify(exactly = 1) { serviceController.stop() }
    }
}
