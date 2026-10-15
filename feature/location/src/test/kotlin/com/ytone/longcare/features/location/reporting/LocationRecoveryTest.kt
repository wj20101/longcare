package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.features.location.core.LocationForegroundServiceController
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRecoveryTest {
    private val identity = LocationAccount(1, 2, 3)
    private val record = LocationRecoveryRecord(identity, 10, "recovered-owner")
    private val keepAlive = mockk<LocationKeepAliveManager>(relaxed = true)
    private val orders = mockk<OrderRepository>()
    private val store = mockk<LocationRecoveryStore>(relaxed = true)

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        every { keepAlive.acquireOrderTracking(any(), any()) } returns true
        coEvery { store.read() } returns record
        coEvery { orders.getOrderState(10) } returns active()
    }

    @Test fun `cold launch offers manual recovery without starting SDK or order query`() = runTest {
        val response = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { orders.getOrderState(10) } coAnswers { response.await() }
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity)
        runCurrent()
        assertEquals(record, manager.pendingRecovery.value)
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        coVerify(exactly = 0) { orders.getOrderState(any()) }
        coVerify(exactly = 0) { store.clear(any()) }
        manager.resumePendingRecovery()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(record.sessionId, 10) }
        val session = requireNotNull(manager.sessionFor(record.sessionId, 10))
        assertFalse(session.canUpload())
        runCurrent()
        response.complete(active())
        runCurrent()
        assertTrue(session.canUpload())
        manager.stopReporting()
    }

    @Test fun `system restoration adopts identity but cannot upload before fresh order confirmation`() = runTest {
        val response = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { orders.getOrderState(10) } coAnswers { response.await() }
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        val session = requireNotNull(manager.restore(record, identity)).first
        assertFalse(session.canUpload())
        runCurrent()
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        response.complete(active())
        runCurrent()
        assertTrue(session.canUpload())
        manager.stopReporting()
    }

    @Test fun `completed order is cleared and never uploads after system restoration`() = runTest {
        coEvery { orders.getOrderState(10) } returns ApiResult.Success(ServiceOrderStateModel(10, 2))
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        val session = requireNotNull(manager.restore(record, identity)).first
        runCurrent()
        assertFalse(session.isValid())
        assertFalse(session.canUpload())
        assertNull(manager.sessionFor(record.sessionId, record.orderId))
        coVerify { store.clear(null) }
        verify(exactly = 1) { keepAlive.release(record.sessionId) }
    }

    @Test fun `immediately completed restored order releases the actual adopted service`() = runTest {
        val controller = mockk<LocationForegroundServiceController>(relaxed = true)
        val actualKeepAlive = LocationKeepAliveManager(controller)
        coEvery { orders.getOrderState(10) } returns ApiResult.Success(ServiceOrderStateModel(10, 2))
        val immediateScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val manager = LocationReportingManager(actualKeepAlive, orders, immediateScope, store)

        val (session, generation) = requireNotNull(manager.restore(record, identity))

        assertFalse(session.isValid())
        assertFalse(actualKeepAlive.hasAcceptedStart())
        assertFalse(actualKeepAlive.onServiceStarted(generation))
        verify(exactly = 1) { controller.stop() }
        verify(exactly = 0) { controller.start(any(), any(), any()) }
    }

    @Test fun `explicit stop clears a cold recovery record even before adoption`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity)
        runCurrent()
        assertEquals(record, manager.pendingRecovery.value)

        manager.stopReporting()
        runCurrent()

        assertNull(manager.pendingRecovery.value)
        coVerify(exactly = 1) { store.clear(null) }
    }

    @Test fun `other account cannot restore persisted order`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity.copy(accountId = 99))
        assertNull(manager.restore(record, identity))
        runCurrent()
        assertNull(manager.pendingRecovery.value)
        coVerify(exactly = 0) { orders.getOrderState(any()) }
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
    }

    @Test fun `unknown authorization pauses rather than deleting wanted recovery and logout stops it`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        val session = requireNotNull(manager.restore(record, identity)).first
        runCurrent()
        assertTrue(session.canUpload())
        manager.pauseAuthorization()
        assertFalse(session.canUpload())
        advanceTimeBy(60_000)
        runCurrent()
        coVerify(exactly = 0) { store.clear(any()) }
        manager.setAccount(identity)
        assertTrue(session.canUpload())
        manager.setAccount(null)
        assertFalse(session.isValid())
        runCurrent()
        coVerify { store.clear(null) }
        verify(exactly = 1) { keepAlive.release(record.sessionId) }
    }

    @Test fun `visible retry retries rejected foreground dispatch without restarting from polling`() = runTest {
        every { keepAlive.acquireOrderTracking(any(), any()) } returnsMany listOf(false, true)
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity)
        manager.prepareOrderStart(10)
        assertEquals(LocationReportingStatus.INTERRUPTED, manager.status.value)
        manager.onOrderStarted(10)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), any()) }
        manager.onAppVisible()
        verify(exactly = 2) { keepAlive.acquireOrderTracking(any(), any()) }
        manager.stopReporting()
    }

    @Test fun `pending preparation releases on failure but confirmed start stays active`() = runTest {
        coEvery { orders.getOrderState(10) } returns ApiResult.Success(ServiceOrderStateModel(10, 0))
        var owner = ""
        every { keepAlive.acquireOrderTracking(any(), any()) } answers { owner = firstArg(); true }
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity)
        manager.prepareOrderStart(10)
        runCurrent()
        assertFalse(requireNotNull(manager.sessionFor(owner, 10)).canUpload())
        manager.cancelOrderPreparation(10)
        verify(exactly = 1) { keepAlive.release(owner) }
        manager.prepareOrderStart(10)
        manager.onOrderStarted(10)
        manager.cancelOrderPreparation(10)
        assertTrue(requireNotNull(manager.sessionFor(owner, 10)).canUpload())
        manager.stopReporting()
    }

    @Test fun `late storage read cannot offer a stopped session for recovery`() = runTest {
        val response = CompletableDeferred<LocationRecoveryRecord?>()
        coEvery { store.read() } coAnswers { response.await() }
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.setAccount(identity)
        runCurrent()
        manager.stopReporting()
        response.complete(record)
        runCurrent()
        assertNull(manager.pendingRecovery.value)
    }

    @Test fun `same active session writes identity once instead of every state poll`() = runTest {
        val manager = LocationReportingManager(keepAlive, orders, backgroundScope, store)
        manager.restore(record, identity)
        runCurrent()
        advanceTimeBy(90_000)
        runCurrent()
        coVerify(exactly = 1) { store.save(record) }
        manager.stopReporting()
    }

    private fun active(): ApiResult<ServiceOrderStateModel> = ApiResult.Success(ServiceOrderStateModel(10, 1))
}
