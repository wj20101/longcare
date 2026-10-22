package com.ytone.longcare.features.location.core

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
import org.robolectric.annotation.Config

/** Desired lifecycle guarantees when a location foreground service cannot be started. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
        every { controller.start(any(), any(), any()) } throws
            IllegalStateException("foreground start rejected")

        val manager = LocationKeepAliveManager(
            serviceController = controller,
        )

        manager.acquireOrderTracking("reporting-owner", 1)
        manager.acquireOrderTracking("reporting-owner", 1)

        verify(exactly = 2) { controller.start("reporting-owner", any(), 1) }
    }

    @Test
    fun `failed foreground service start must not begin unprotected background collection`() {
        val controller = mockk<LocationForegroundServiceController>()
        every { controller.start(any(), any(), any()) } throws
            IllegalStateException("foreground start rejected")

        val manager = LocationKeepAliveManager(
            serviceController = controller,
        )

        manager.acquireOrderTracking("reporting-owner", 1)

        verify(exactly = 1) { controller.start("reporting-owner", any(), 1) }
    }

    @Test
    fun `new acquire waits until the previous service stop command is complete`() {
        val stopEntered = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        val controller = mockk<LocationForegroundServiceController>()
        every { controller.start(any(), any(), any()) } just runs
        every { controller.stop() } answers {
            stopEntered.countDown()
            check(allowStop.await(2, TimeUnit.SECONDS))
            true
        }
        val manager = LocationKeepAliveManager(controller)
        manager.acquireOrderTracking("reporting-owner", 1)

        val releaseThread = Thread { manager.release("reporting-owner") }
        releaseThread.start()
        org.junit.Assert.assertTrue(stopEntered.await(2, TimeUnit.SECONDS))
        val acquireThread = Thread { manager.acquireOrderTracking("reporting-owner", 1) }
        acquireThread.start()
        org.junit.Assert.assertTrue("acquire must be serialized behind stop", acquireThread.isAlive)
        allowStop.countDown()
        releaseThread.join(2_000)
        acquireThread.join(2_000)

        verify(exactly = 2) { controller.start("reporting-owner", any(), 1) }
        verify(exactly = 1) { controller.stop() }
    }

    @Test
    fun `background or missing runtime readiness never dispatches service and visible recovery retries`() {
        var importance = android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        var permission = true
        var enabled = true
        mockkStatic(android.app.ActivityManager::class)
        every { android.app.ActivityManager.getMyMemoryState(any()) } answers {
            firstArg<android.app.ActivityManager.RunningAppProcessInfo>().importance = importance
        }
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.startForegroundService(any()) } returns android.content.ComponentName(
            "com.ytone.longcare", "com.ytone.longcare.features.location.service.LocationTrackingService",
        )
        val readiness = mockk<com.ytone.longcare.domain.location.LocationRuntimeReadiness> {
            every { hasLocationPermission() } answers { permission }
            every { isLocationServiceEnabled() } answers { enabled }
        }
        val manager = LocationKeepAliveManager(LocationForegroundServiceController(context, readiness))
        manager.acquireOrderTracking("owner", 1)
        importance = android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        permission = false
        manager.acquireOrderTracking("owner", 1)
        permission = true
        enabled = false
        manager.acquireOrderTracking("owner", 1)
        verify(exactly = 0) { context.startForegroundService(any()) }
        enabled = true
        org.junit.Assert.assertTrue(manager.acquireOrderTracking("owner", 1))
        verify(exactly = 1) { context.startForegroundService(any()) }
    }

    @Test
    @Config(sdk = [24, 28])
    fun `silent platform rejection rolls back acceptance and a later visible request retries`() {
        mockkStatic(android.app.ActivityManager::class)
        every { android.app.ActivityManager.getMyMemoryState(any()) } answers {
            firstArg<android.app.ActivityManager.RunningAppProcessInfo>().importance =
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val readiness = mockk<com.ytone.longcare.domain.location.LocationRuntimeReadiness> {
            every { hasLocationPermission() } returns true
            every { isLocationServiceEnabled() } returns true
        }
        val context = mockk<android.content.Context>()
        every { context.packageName } returns "com.ytone.longcare"
        val component = android.content.ComponentName(
            "com.ytone.longcare", "com.ytone.longcare.features.location.service.LocationTrackingService",
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            every { context.startForegroundService(any()) } returnsMany listOf(null, component)
        } else {
            every { context.startService(any()) } returnsMany listOf(null, component)
        }
        val manager = LocationKeepAliveManager(LocationForegroundServiceController(context, readiness))

        org.junit.Assert.assertFalse(manager.acquireOrderTracking("owner", 1))
        // The two native dispatches verified below prove that the rejected start was rolled back.
        org.junit.Assert.assertTrue(manager.acquireOrderTracking("owner", 1))
        // A genuinely accepted second dispatch remains idempotent.
        org.junit.Assert.assertTrue(manager.acquireOrderTracking("owner", 1))

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            verify(exactly = 2) { context.startForegroundService(any()) }
            verify(exactly = 0) { context.startService(any()) }
        } else {
            verify(exactly = 2) { context.startService(any()) }
        }
    }
}
