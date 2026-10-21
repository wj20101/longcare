package com.ytone.longcare.features.location.service

import android.app.Application
import android.content.Intent
import android.os.PowerManager
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.location.core.LocationForegroundServiceController
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.features.location.reporting.LocationReportingStatus
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Actual Service lifecycle and session coordinator; only SDK, runtime conditions and HTTP are ports. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LocationTrackingServiceSamplingRecoveryTest {
    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test fun `initial order wait is diagnosed without occupying the sign in location client`() = runTest {
        val events = mutableListOf<Map<String, Any?>>()
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackEvent(any(), any(), any(), any()) } answers { events += secondArg<Map<String, Any?>>() }
        withFixture {
            start(ready = false)
            assertEquals("authorization_wait", events.single()["stage"])
            advanceTimeBy(90_000)
            runCurrent()
            verify(exactly = 0) { service.continuousAmapLocationManager.startContinuousLocation(any(), any()) }
            assertEquals(1, events.size)
            manager.onOrderStarted(10)
            runCurrent()
            val finished = events.single { it["stage"] == "authorization_wait_finished" }
            assertEquals("order_ready", finished["reason"])
            assertEquals(90_000L, finished["waitMs"])
            assertEquals(1, subscriptions)
        }
    }

    @Test fun `disabled system location pauses collection and resumes without another visible start`() = runTest {
        withFixture {
            start()
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }

            enabled = false
            tick()
            assertEquals(1, subscriptions)
            assertFalse(samplingEnabled)
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertTrue(shadowOf(service).lastForegroundNotification != null)
            verify(exactly = 0) { controller.stop() }

            enabled = true
            tick()
            assertEquals(1, subscriptions)
            assertTrue(samplingEnabled)
            assertEquals(1, startedSubscriptions)
            sample()
            coVerify(exactly = 2) { service.locationRepository.addPosition(10, any(), any()) }
            verify(exactly = 1) { controller.start(any(), any(), 10) }
            assertEquals(LocationReportingStatus.ACTIVE, manager.status.value)
        }
    }

    @Test fun `error four callbacks do not replace the ongoing SDK subscription`() = runTest {
        withFixture {
            start()
            repeat(8) {
                failure(LocationFailure.NETWORK, errorCode = 4)
                tick()
            }
            assertEquals(1, subscriptions)
            assertEquals(1, startedSubscriptions)
            assertEquals(0, recoveries)
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }
            assertEquals(LocationReportingStatus.ACTIVE, manager.status.value)
        }
    }

    @Test fun `health checks detect a disabled location gate at fifteen seconds`() = runTest {
        withFixture {
            start()
            enabled = false
            advanceTimeBy(14_999L)
            runCurrent()
            assertTrue(samplingEnabled)
            advanceTimeBy(1L)
            runCurrent()
            assertFalse(samplingEnabled)
            assertEquals(1, subscriptions)
            assertFalse(shadowOf(service).isStoppedBySelf)
        }
    }

    @Test fun `resuming sampling on a health tick defers SDK initialization compensation`() = runTest {
        withFixture {
            start()
            enabled = false
            advanceTimeBy(15_000L)
            runCurrent()
            enabled = true
            advanceTimeBy(15_000L)
            runCurrent()
            coVerify(exactly = 1) { service.continuousAmapLocationManager.syncAppVisibility(false) }
            coVerify(exactly = 0) { service.continuousAmapLocationManager.syncAppVisibility(true) }
            advanceTimeBy(15_000L)
            runCurrent()
            coVerify(exactly = 1) { service.continuousAmapLocationManager.syncAppVisibility(true) }
        }
    }

    @Test fun `health checks retain a spent recovery budget after error and successful callbacks`() = runTest {
        withFixture {
            start()
            // The same owner's foreground action/previous collector already used its recovery.
            recoveryAttempted = true
            repeat(8) { tick() }
            assertEquals(0, recoveries)
            assertEquals(1, subscriptions)
            assertFalse(shadowOf(service).isStoppedBySelf)

            failure(LocationFailure.NETWORK, errorCode = 4)
            repeat(3) { tick() }
            assertEquals(0, recoveries)
            sample()
            repeat(3) { tick() }
            assertEquals(0, recoveries)
            assertEquals(1, subscriptions)
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }
        }
    }

    @Test fun `returning visible resumes a restored location gate immediately without restarting collection`() = runTest {
        withFixture {
            start()
            enabled = false
            tick()
            assertFalse(samplingEnabled)
            enabled = true
            repeat(3) { manager.onAppVisible() }
            runCurrent() // No 30-second tick and no second Service start.
            assertTrue(samplingEnabled)
            assertEquals(1, startedSubscriptions)
            verify(exactly = 1) { controller.start(any(), any(), 10) }
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, 31.0, 121.0) }
        }
    }

    @Test fun `visible refresh cannot enable sampling while the current account is unresolved`() = runTest {
        withFixture {
            start()
            users.value = SessionState.Unknown
            tick()
            assertFalse(samplingEnabled)
            manager.onAppVisible()
            runCurrent()
            assertFalse(samplingEnabled)
            sample()
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
        }
    }

    @Test fun `visible refresh rechecks revoked permission instead of enabling sampling`() = runTest {
        withFixture {
            start()
            permission = false
            manager.onAppVisible()
            runCurrent()
            assertEquals(0, subscriptions)
            assertTrue(shadowOf(service).isStoppedBySelf)
            sample()
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
        }
    }

    @Test fun `pausing cancels an in-flight request and resuming waits for a new sample`() = runTest {
        withFixture {
            var requestCancelled = false
            coEvery { service.locationRepository.addPosition(any(), any(), any()) } coAnswers {
                try { awaitCancellation() }
                finally { requestCancelled = true }
            }
            start()
            advanceTimeBy(15_000L)
            runCurrent()
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }

            enabled = false
            tick()
            assertTrue(requestCancelled)
            enabled = true
            tick()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }

            coEvery { service.locationRepository.addPosition(any(), any(), any()) } returns ApiResult.Success(Unit)
            sample()
            coVerify(exactly = 2) { service.locationRepository.addPosition(10, any(), any()) }
        }
    }

    @Test fun `continued silence after one rebuild keeps listening and a late sample resumes upload`() = runTest {
        withFixture {
            start()
            repeat(8) { tick() }
            assertEquals(1, recoveries)
            assertEquals(1, subscriptions)
            assertFalse(shadowOf(service).isStoppedBySelf)
            verify(exactly = 0) { controller.stop() }

            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }
            assertEquals(LocationReportingStatus.ACTIVE, manager.status.value)
            verify(exactly = 1) { controller.start(any(), any(), 10) }
            repeat(3) { tick() }
            assertEquals(1, recoveries)
            assertEquals(1, subscriptions)
            assertFalse(shadowOf(service).isStoppedBySelf)
        }
    }

    @Test fun `battery exempt idle service repairs silence once and resumes correct uploads`() = runTest {
        withFixture {
            val power = shadowOf(service.getSystemService(PowerManager::class.java))
            power.setIsDeviceIdleMode(true)
            power.setIgnoringBatteryOptimizations(service.packageName, true)
            start()
            repeat(8) { tick() }
            assertEquals(1, recoveries)
            assertEquals(1, subscriptions)
            assertTrue(shadowOf(service).lastForegroundNotification != null)
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, 31.0, 121.0) }
            assertEquals(LocationReportingStatus.ACTIVE, manager.status.value)
        }
    }

    @Test fun `restricted idle service waits without repeated SDK rebuilds then repairs on exit`() = runTest {
        withFixture {
            val power = shadowOf(service.getSystemService(PowerManager::class.java))
            power.setIsDeviceIdleMode(true)
            power.setIgnoringBatteryOptimizations(service.packageName, false)
            start()
            repeat(8) { tick() }
            assertEquals(0, recoveries)
            assertEquals(1, subscriptions)
            power.setIsDeviceIdleMode(false)
            tick()
            assertEquals(1, recoveries)
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, 31.0, 121.0) }
        }
    }

    @Test fun `synchronous callback during rebuild cannot grant another automatic rebuild`() = runTest {
        withFixture {
            coEvery { service.continuousAmapLocationManager.recoverStalledSampling(any()) } coAnswers {
                if (recoveryAttempted || testScheduler.currentTime - lastCallback < 90_000L) false
                else {
                    recoveryAttempted = true
                    recoveries += 1
                    // The SDK can invoke its listener inside startLocation, before recovery returns.
                    recordCallback()
                    true
                }
            }
            start()
            repeat(3) { tick() }
            assertEquals(1, recoveries)
            assertEquals(1L, callbacks)
            assertEquals(90_000L, lastCallback)

            repeat(3) { tick() }
            assertEquals(1, recoveries)
            assertEquals(1L, callbacks)
            assertEquals(90_000L, lastCallback)
            assertEquals(1, subscriptions)
            assertFalse(shadowOf(service).isStoppedBySelf)
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
        }
    }

    @Test fun `permission loss stops collection and a later permission grant cannot revive the old collector`() = runTest {
        withFixture {
            start()
            permission = false
            tick()
            assertEquals(0, subscriptions)
            assertTrue(shadowOf(service).isStoppedBySelf)
            permission = true
            tick()
            assertEquals(0, subscriptions)
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
        }
    }

    @Test fun `privacy withdrawal terminates the active order collector`() = runTest {
        withFixture {
            start()
            consent = false
            tick()
            assertEquals(0, subscriptions)
            assertTrue(shadowOf(service).isStoppedBySelf)
            sample()
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
        }
    }

    @Test fun `unknown login state pauses instead of ending the order and resumes when identity is confirmed`() = runTest {
        withFixture {
            start()
            users.value = SessionState.Unknown
            tick()
            assertEquals(1, subscriptions)
            assertFalse(samplingEnabled)
            assertFalse(shadowOf(service).isStoppedBySelf)
            users.value = SessionState.LoggedIn(user)
            tick()
            assertEquals(1, subscriptions)
            assertTrue(samplingEnabled)
            assertEquals(1, startedSubscriptions)
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }
            verify(exactly = 1) { controller.start(any(), any(), 10) }
        }
    }

    @Test fun `ending an order cancels its collector before a late callback can upload`() = runTest {
        withFixture {
            start()
            manager.onOrderEnded(10)
            runCurrent()
            assertEquals(0, subscriptions)
            sample()
            coVerify(exactly = 0) { service.locationRepository.addPosition(any(), any(), any()) }
            verify(exactly = 1) { controller.stop() }
        }
    }

    @Test fun `sticky recreation with location disabled retains the order and resumes inside the existing service`() = runTest {
        withFixture {
            enabled = false
            restore()
            assertEquals(1, subscriptions)
            assertFalse(samplingEnabled)
            assertFalse(shadowOf(service).isStoppedBySelf)
            verify(exactly = 0) { controller.start(any(), any(), any()) }

            enabled = true
            tick()
            assertTrue(samplingEnabled)
            sample()
            coVerify(exactly = 1) { service.locationRepository.addPosition(10, any(), any()) }
            verify(exactly = 0) { controller.start(any(), any(), any()) }
        }
    }

    private suspend fun TestScope.withFixture(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(this)
        try { fixture.block() }
        finally {
            fixture.manager.stopReporting()
            fixture.service.onDestroy()
            runCurrent()
        }
    }

    private class Fixture(private val scope: TestScope) {
        val controller = mockk<LocationForegroundServiceController>(relaxed = true)
        private val keepAlive = LocationKeepAliveManager(controller)
        private val orders = mockk<OrderRepository>()
        private val store = mockk<LocationRecoveryStore>(relaxed = true)
        val manager = LocationReportingManager(keepAlive, orders, scope.backgroundScope, store)
        val user = User(companyId = 1, accountId = 2, userId = 3)
        val users = MutableStateFlow<SessionState>(SessionState.LoggedIn(user))
        private val samples = MutableSharedFlow<LocationAcquisition>(extraBufferCapacity = 1)
        var enabled = true
        var permission = true
        var consent = true
        var subscriptions = 0
        var startedSubscriptions = 0
        var recoveries = 0
        var recoveryAttempted = false
        var samplingEnabled = true
        var lastCallback = 0L
        var callbacks = 0L
        private var owner = ""
        private var generation = 0L
        val service = Robolectric.buildService(LocationTrackingService::class.java).get()
        val testScheduler get() = scope.testScheduler

        init {
            every { controller.start(any(), any(), any()) } answers {
                owner = firstArg()
                generation = secondArg()
            }
            coEvery { store.read() } returns null
            coEvery { orders.getOrderState(any()) } coAnswers { awaitCancellation() }
            service.apply {
                keepAliveManager = keepAlive
                reportingManager = manager
                continuousAmapLocationManager = mockk(relaxed = true)
                locationRepository = mockk(relaxed = true)
                locationClock = mockk()
                recoveryStore = store
                userSessionRepository = mockk { every { sessionState } returns users }
                runtimeReadiness = mockk {
                    every { hasLocationPermission() } answers { permission }
                    every { isLocationServiceEnabled() } answers { enabled }
                }
                privacyConsentManager = mockk { every { isPrivacyConsented } answers { consent } }
            }
            every { service.locationClock.currentTimeMillis() } answers { WALL_START + testScheduler.currentTime }
            every { service.locationClock.elapsedRealtime() } answers { testScheduler.currentTime }
            every { service.continuousAmapLocationManager.startContinuousLocation(any(), any()) } answers {
                samplingEnabled = secondArg()
                flow {
                    subscriptions += 1
                    startedSubscriptions += 1
                    lastCallback = testScheduler.currentTime
                    try { emitAll(samples) }
                    finally { subscriptions -= 1 }
                }
            }
            coEvery { service.continuousAmapLocationManager.setSamplingEnabled(any(), any()) } coAnswers {
                val enabled = secondArg<Boolean>()
                if (samplingEnabled != enabled) {
                    recoveryAttempted = false
                    if (enabled) lastCallback = testScheduler.currentTime
                }
                samplingEnabled = enabled
            }
            coEvery { service.continuousAmapLocationManager.isSamplingStalled(any()) } coAnswers {
                samplingEnabled && testScheduler.currentTime - lastCallback >= 90_000L
            }
            coEvery { service.continuousAmapLocationManager.recoverStalledSampling(any()) } coAnswers {
                if (recoveryAttempted || testScheduler.currentTime - lastCallback < 90_000L) false
                else {
                    recoveryAttempted = true
                    recoveries += 1
                    lastCallback = testScheduler.currentTime
                    true
                }
            }
            coEvery { service.locationRepository.addPosition(any(), any(), any()) } returns ApiResult.Success(Unit)
            manager.setAccount(LocationAccount(1, 2, 3))
        }

        fun start(ready: Boolean = true) {
            scope.runCurrent()
            manager.prepareOrderStart(10)
            if (ready) manager.onOrderStarted(10)
            service.onStartCommand(Intent().apply {
                action = LocationTrackingService.ACTION_ACQUIRE_KEEP_ALIVE
                putExtra(LocationTrackingService.EXTRA_OWNER, owner)
                putExtra(LocationTrackingService.EXTRA_GENERATION, generation)
                putExtra(LocationTrackingService.EXTRA_ORDER_ID, 10L)
            }, 0, 1)
            scope.runCurrent()
            assertEquals(if (ready) 1 else 0, subscriptions)
        }

        fun restore() {
            coEvery { store.read() } returns LocationRecoveryRecord(LocationAccount(1, 2, 3), 10, "restored-order")
            coEvery { orders.getOrderState(10) } returns ApiResult.Success(
                ServiceOrderStateModel(10, ServiceOrderStateModel.STATE_IN_PROGRESS),
            )
            service.onStartCommand(null, 0, 1)
            scope.runCurrent()
        }

        fun tick() {
            scope.advanceTimeBy(30_000L)
            scope.runCurrent()
        }

        suspend fun sample() {
            val value = location()
            recordCallback()
            samples.emit(LocationAcquisition.Success(value))
            scope.runCurrent()
        }

        suspend fun failure(reason: LocationFailure, errorCode: Int) {
            recordCallback()
            samples.emit(LocationAcquisition.Failure(reason, location().copy(errorCode = errorCode)))
            scope.runCurrent()
        }

        fun recordCallback() {
            lastCallback = testScheduler.currentTime
            callbacks += 1L
        }

        fun location() = LocationResult(
            latitude = 31.0,
            longitude = 121.0,
            provider = "network",
            accuracy = 50f,
            coordType = "GCJ02",
            locationType = 5,
            trustedLevel = 1,
            locationTime = WALL_START + testScheduler.currentTime,
            receivedAt = WALL_START + testScheduler.currentTime,
            receivedElapsedRealtime = testScheduler.currentTime,
        )

        private companion object { const val WALL_START = 1_000_000L }
    }
}
