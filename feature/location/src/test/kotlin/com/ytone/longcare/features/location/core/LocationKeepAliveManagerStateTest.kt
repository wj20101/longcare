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

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        every { serviceController.start(any(), any()) } just runs
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

        manager.acquire("order")
        val generation = (manager.state.value as LocationKeepAliveState.Starting).generation
        manager.onServiceStarted(generation)
        manager.acquire("order")

        assertEquals(LocationKeepAliveState.Active(generation, 1), manager.state.value)
        verify(exactly = 1) { serviceController.start("order", any()) }
    }

    @Test
    fun `old generation stop cannot overwrite a newer start`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquire("first")
        val first = (manager.state.value as LocationKeepAliveState.Starting).generation
        manager.release("first")
        manager.acquire("second")
        val second = (manager.state.value as LocationKeepAliveState.Starting).generation

        manager.onServiceStopped(first)

        assertEquals(LocationKeepAliveState.Starting(second, 1), manager.state.value)
    }

    @Test
    fun `stale service start is rejected before SDK collection`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquire("first")
        val first = (manager.state.value as LocationKeepAliveState.Starting).generation
        manager.release("first")
        manager.acquire("second")
        val second = (manager.state.value as LocationKeepAliveState.Starting).generation

        assertTrue(!manager.onServiceStarted(first))
        assertEquals(LocationKeepAliveState.Starting(second, 1), manager.state.value)
        assertTrue(manager.onServiceStarted(second))
        assertEquals(LocationKeepAliveState.Active(second, 1), manager.state.value)
    }

    @Test
    fun `visible retry restarts an unexpectedly destroyed active service`() {
        val manager = LocationKeepAliveManager(serviceController)
        manager.acquire("order")
        val first = (manager.state.value as LocationKeepAliveState.Starting).generation
        manager.onServiceStarted(first)
        manager.onServiceStopped(first)

        assertTrue(manager.state.value is LocationKeepAliveState.NeedsUserRestart)
        manager.acquire("order")

        val restarted = manager.state.value as LocationKeepAliveState.Starting
        assertEquals(first + 1, restarted.generation)
        verify(exactly = 2) { serviceController.start("order", any()) }
    }
}
