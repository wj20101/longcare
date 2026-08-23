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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Desired lifecycle guarantees when a location foreground service cannot be started. */
@RunWith(RobolectricTestRunner::class)
class LocationKeepAliveManagerRegressionTest {

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackError(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `failed foreground service start must roll back owner so a later acquire retries`() {
        val controller = mockk<LocationForegroundServiceController>()
        every { controller.start(any(), any()) } throws
            IllegalStateException("foreground start rejected")

        val manager = LocationKeepAliveManager(
            serviceController = controller,
        )

        manager.acquire("reporting-owner")
        manager.acquire("reporting-owner")

        verify(exactly = 2) { controller.start("reporting-owner", any()) }
    }

    @Test
    fun `failed foreground service start must not begin unprotected background collection`() {
        val controller = mockk<LocationForegroundServiceController>()
        every { controller.start(any(), any()) } throws
            IllegalStateException("foreground start rejected")

        val manager = LocationKeepAliveManager(
            serviceController = controller,
        )

        manager.acquire("reporting-owner")

        verify(exactly = 1) { controller.start("reporting-owner", any()) }
    }

    @Test
    fun `new acquire waits until the previous service stop command is complete`() {
        val stopEntered = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        val controller = mockk<LocationForegroundServiceController>()
        every { controller.start(any(), any()) } just runs
        every { controller.stop() } answers {
            stopEntered.countDown()
            check(allowStop.await(2, TimeUnit.SECONDS))
            true
        }
        val manager = LocationKeepAliveManager(controller)
        manager.acquire("reporting-owner")

        val releaseThread = Thread { manager.release("reporting-owner") }
        releaseThread.start()
        org.junit.Assert.assertTrue(stopEntered.await(2, TimeUnit.SECONDS))
        val acquireThread = Thread { manager.acquire("reporting-owner") }
        acquireThread.start()
        org.junit.Assert.assertTrue("acquire must be serialized behind stop", acquireThread.isAlive)
        allowStop.countDown()
        releaseThread.join(2_000)
        acquireThread.join(2_000)

        verify(exactly = 2) { controller.start("reporting-owner", any()) }
        verify(exactly = 1) { controller.stop() }
    }
}
