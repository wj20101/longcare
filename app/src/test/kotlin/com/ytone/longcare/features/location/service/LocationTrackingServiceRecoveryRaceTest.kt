package com.ytone.longcare.features.location.service

import android.app.Application
import android.content.Intent
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.location.core.LocationForegroundServiceController
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import io.mockk.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LocationTrackingServiceRecoveryRaceTest {
    private val controller = mockk<LocationForegroundServiceController>(relaxed = true)
    private val keepAlive = LocationKeepAliveManager(controller)
    private val orders = mockk<OrderRepository>()
    private val store = mockk<LocationRecoveryStore>(relaxed = true)
    private var owner = ""
    private var generation = 0L

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        Dispatchers.setMain(StandardTestDispatcher())
        every { controller.start(any(), any(), any()) } answers {
            owner = firstArg()
            generation = secondArg()
        }
        coEvery { store.read() } returns null
        coEvery { orders.getOrderState(any()) } coAnswers { awaitCancellation() }
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test fun `late empty bootstrap cannot stop a new visible session`() = assertLateBootstrap(false)

    @Test fun `late failed bootstrap cannot stop a new visible session`() = assertLateBootstrap(true)

    private fun assertLateBootstrap(fails: Boolean) = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(LocationAccount(1, 2, 3))
        runCurrent()
        val readEntered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val readJob = AtomicReference<Job>()
        val result = CompletableDeferred<LocationRecoveryRecord?>()
        coEvery { store.read() } coAnswers {
            readJob.set(currentCoroutineContext().job)
            readEntered.countDown()
            // Simulate storage that finishes despite cancellation.
            withContext(NonCancellable) { result.await() }
        }
        val service = service(manager)
        try {
            service.onStartCommand(null, 0, 1)
            assertTrue(readEntered.await(2, TimeUnit.SECONDS))
            readJob.get().invokeOnCompletion { finished.countDown() }
            manager.prepareOrderStart(10)
            service.onStartCommand(startIntent(10), 0, 2)

            if (fails) result.completeExceptionally(IllegalStateException("old read failed"))
            else result.complete(null)
            assertTrue(finished.await(2, TimeUnit.SECONDS))

            assertFalse(shadowOf(service).isStoppedBySelf)
            verify(exactly = 1) { controller.start(any(), any(), 10) }
            verify(exactly = 0) { controller.stop() }
        } finally {
            result.complete(null)
            manager.stopReporting()
            service.onDestroy()
            runCurrent()
        }
    }

    @Test fun `task removal invalidates immediately and delayed storage clear cannot stop new order`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(LocationAccount(1, 2, 3))
        runCurrent()
        val clearFinished = CompletableDeferred<Unit>()
        coEvery { store.clear(any()) } coAnswers { clearFinished.await() }
        val service = service(manager)
        try {
            manager.prepareOrderStart(10)
            service.onStartCommand(startIntent(10), 0, 1)
            service.onTaskRemoved(null)
            assertTrue(shadowOf(service).isStoppedBySelf)
            verify(exactly = 1) { controller.stop() }
            runCurrent()

            manager.prepareOrderStart(20)
            clearFinished.complete(Unit)
            runCurrent()

            verify(exactly = 1) { controller.stop() }
            assertTrue(keepAlive.acquireOrderTracking(owner, 20))
            verify(exactly = 1) { controller.start(any(), any(), 20) }
        } finally {
            clearFinished.complete(Unit)
            manager.stopReporting()
            service.onDestroy()
            runCurrent()
        }
    }

    @Test fun `empty bootstrap stops an unclaimed service even when a destroyed session still wants recovery`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(LocationAccount(1, 2, 3))
        runCurrent()
        val previous = service(manager)
        manager.prepareOrderStart(10)
        previous.onStartCommand(startIntent(10), 0, 1)
        previous.onDestroy()
        val finished = CountDownLatch(1)
        coEvery { store.read() } coAnswers {
            currentCoroutineContext().job.invokeOnCompletion { finished.countDown() }
            null
        }
        val restored = service(manager)
        try {
            restored.onStartCommand(null, 0, 2)
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(shadowOf(restored).isStoppedBySelf)

            manager.onAppVisible()
            verify(exactly = 2) { controller.start(any(), any(), 10) }
        } finally {
            manager.stopReporting()
            restored.onDestroy()
            runCurrent()
        }
    }

    private fun startIntent(orderId: Long) = Intent().apply {
        action = LocationTrackingService.ACTION_ACQUIRE_KEEP_ALIVE
        putExtra(LocationTrackingService.EXTRA_OWNER, owner)
        putExtra(LocationTrackingService.EXTRA_GENERATION, generation)
        putExtra(LocationTrackingService.EXTRA_ORDER_ID, orderId)
    }

    private fun service(manager: LocationReportingManager) =
        Robolectric.buildService(LocationTrackingService::class.java).get().apply {
            // Attach the native Service without running Hilt injection; all ports stay local.
            keepAliveManager = keepAlive
            reportingManager = manager
            continuousAmapLocationManager = mockk(relaxed = true)
            locationRepository = mockk(relaxed = true)
            locationClock = mockk(relaxed = true)
            recoveryStore = store
            userSessionRepository = mockk(relaxed = true)
            runtimeReadiness = mockk(relaxed = true)
            privacyConsentManager = mockk {
                every { isPrivacyConsented } returns true
            }
        }
}
