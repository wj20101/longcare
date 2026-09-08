package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationReportingManagerTest {
    private val keepAlive = mockk<LocationKeepAliveManager>(relaxed = true)
    private val repository = mockk<OrderRepository>()

    @Before
    fun setup() { KLogger.updateConfig { enabled = false } }

    @Test
    fun `only confirmed in progress state starts foreground tracking`() = runTest {
        coEvery { repository.getOrderState(1) } returns state(1)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.startReporting(OrderKey(1))
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        runCurrent()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 1) }
        manager.stopReporting()
    }

    @Test
    fun `every non service state forbids foreground tracking`() = runTest {
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        for (state in listOf(-1, 0, 2, 3)) {
            coEvery { repository.getOrderState(1) } returns state(state)
            manager.startReporting(OrderKey(1))
            runCurrent()
        }
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
    }

    @Test
    fun `outage longer than three queries recovers without another UI start`() = runTest {
        coEvery { repository.getOrderState(1) } returnsMany listOf(
            ApiResult.Exception(IllegalStateException("offline")),
            ApiResult.Exception(IllegalStateException("offline")),
            ApiResult.Exception(IllegalStateException("offline")),
            state(1),
        )
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.startReporting(OrderKey(1))
        runCurrent()
        advanceTimeBy(35_000)
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        runCurrent()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 1) }
        manager.stopReporting()
    }

    @Test
    fun `active state query failure does not stop or restart reporting`() = runTest {
        coEvery { repository.getOrderState(1) } returnsMany listOf(
            state(1), ApiResult.Exception(IllegalStateException("offline")), state(1),
        )
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.startReporting(OrderKey(1))
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 1) }
        verify(exactly = 0) { keepAlive.release(any()) }
        manager.stopReporting()
    }

    @Test
    fun `monitoring is shared and remote completion stops without screen confirmation`() = runTest {
        coEvery { repository.getOrderState(1) } returnsMany listOf(state(1), state(2))
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.monitorOrder(1)
        manager.startReporting(OrderKey(1))
        manager.monitorOrder(1)
        runCurrent()
        coVerify(exactly = 1) { repository.getOrderState(1) }
        advanceTimeBy(5_000)
        runCurrent()
        verify(exactly = 1) { keepAlive.release(any()) }
        assertEquals(2, manager.orderState.value?.state)
    }

    @Test
    fun `monitor alone does not request location and old order end cannot stop new order`() = runTest {
        coEvery { repository.getOrderState(any()) } returns state(1)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.monitorOrder(1)
        runCurrent()
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        manager.startReporting(OrderKey(2))
        runCurrent()
        manager.onOrderEnded(1)
        verify(exactly = 0) { keepAlive.release(any()) }
        manager.onOrderEnded(2)
        verify(exactly = 1) { keepAlive.release(any()) }
    }

    @Test
    fun `invalid ids never query state or start location`() = runTest {
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope).apply { setMonitoringAllowed(true) }
        manager.startReporting(OrderKey(0))
        manager.monitorOrder(-1)
        runCurrent()
        coVerify(exactly = 0) { repository.getOrderState(any()) }
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
    }

    private fun state(value: Int): ApiResult<ServiceOrderStateModel> =
        ApiResult.Success(ServiceOrderStateModel(orderId = 1, state = value))

    @Test
    fun `formal start success allows immediate tracking even during state query outage`() = runTest {
        coEvery { repository.getOrderState(1) } returns ApiResult.Exception(IllegalStateException("offline"))
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.onOrderStarted(1)
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        manager.startReporting(OrderKey(1))
        verify(exactly = 1) { keepAlive.acquireOrderTracking(any(), 1) }
        runCurrent()
        advanceTimeBy(35_000)
        runCurrent()
        verify(exactly = 0) { keepAlive.release(any()) }
        manager.stopReporting()
    }

    @Test
    fun `reopened in progress order starts after process state is recreated`() = runTest {
        coEvery { repository.getOrderState(1) } returns state(1)
        val firstProcessScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val firstProcess = LocationReportingManager(keepAlive, repository, firstProcessScope)
        firstProcess.setMonitoringAllowed(true)
        firstProcess.startReporting(OrderKey(1))
        runCurrent()
        // Simulate losing the process scope without stopReporting/onDestroy cleanup.
        firstProcessScope.cancel()

        val nextKeepAlive = mockk<LocationKeepAliveManager>(relaxed = true)
        val nextProcess = LocationReportingManager(nextKeepAlive, repository, backgroundScope)
        nextProcess.setMonitoringAllowed(true)
        runCurrent()
        verify(exactly = 0) { nextKeepAlive.acquireOrderTracking(any(), any()) }
        nextProcess.startReporting(OrderKey(1))
        runCurrent()
        verify(exactly = 1) { nextKeepAlive.acquireOrderTracking(any(), 1) }
        coVerify(exactly = 2) { repository.getOrderState(1) }
        nextProcess.stopReporting()
    }

    @Test
    fun `recreated process never restores an already completed order`() = runTest {
        coEvery { repository.getOrderState(1) } returns state(2)
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(true)
        manager.startReporting(OrderKey(1))
        runCurrent()
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
        assertEquals(2, manager.orderState.value?.state)
    }

    @Test
    fun `late screen callbacks after logout cannot restart polling or upload`() = runTest {
        val manager = LocationReportingManager(keepAlive, repository, backgroundScope)
        manager.setMonitoringAllowed(false)
        manager.monitorOrder(1)
        manager.startReporting(OrderKey(1))
        runCurrent()
        coVerify(exactly = 0) { repository.getOrderState(any()) }
        verify(exactly = 0) { keepAlive.acquireOrderTracking(any(), any()) }
    }
}
