package com.ytone.longcare.features.servicecountdown.vm

import androidx.lifecycle.ViewModelStore
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ServiceCountdownViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var unifiedOrderRepository: OrderDetailRepository
    private lateinit var imageRepository: OrderImageRepository
    private lateinit var serviceOrderLifecycle: ServiceOrderLifecycle
    private lateinit var systemGateway: ServiceCountdownSystemGateway
    private lateinit var viewModel: ServiceCountdownViewModel

    @Before
    fun setup() {
        KLogger.updateConfig { enabled = false }
        unifiedOrderRepository = mockk(relaxed = true)
        imageRepository = mockk(relaxed = true)
        serviceOrderLifecycle = mockk(relaxed = true)
        every { serviceOrderLifecycle.orderState } returns MutableStateFlow(null)
        systemGateway = mockk(relaxed = true)
        
        // Mock default flows
        
        viewModel = ServiceCountdownViewModel(
            unifiedOrderRepository,
            imageRepository,
            serviceOrderLifecycle,
            systemGateway
        )
    }

    @After fun clearViewModel() {
        ViewModelStore().apply { put("countdown", viewModel) }.clear()
    }

    @Test
    fun `ending service stops platform work through gateway`() = runTest {
        val orderKey = OrderKey(orderId = 12345L, planId = 1)

        viewModel.onOrderEnded(orderKey)
        advanceUntilIdle()

        verify(exactly = 1) { systemGateway.cancelCountdownAlarmForOrder(orderKey) }
        coVerify(exactly = 1) { unifiedOrderRepository.endLocalService(orderKey) }
        verify(exactly = 0) { serviceOrderLifecycle.onOrderEnded(any()) }
    }

    @Test
    fun `confirmed local end finishes even when navigation clears the ViewModel`() = runTest {
        val order = OrderKey(12345L, 1)
        val started = CompletableDeferred<Unit>()
        val commit = CompletableDeferred<Unit>()
        var persisted = false
        coEvery { unifiedOrderRepository.endLocalService(order) } coAnswers {
            started.complete(Unit)
            commit.await()
            persisted = true
        }

        viewModel.onOrderEnded(order)
        assertTrue(started.isCompleted)
        ViewModelStore().apply { put("countdown", viewModel) }.clear()
        commit.complete(Unit)
        runCurrent()

        assertTrue(persisted)
    }

    @Test
    fun `startOrderStatePolling observes the shared business session`() = runTest {
        // Given
        val orderKey = OrderKey(orderId = 12345L, planId = 1)
        
        // When
        viewModel.startOrderStatePolling(orderKey)
        
        runCurrent()
        
        // The screen subscribes to one business-owned monitor, rather than querying independently.
        verify(exactly = 1) { serviceOrderLifecycle.monitorOrder(12345L) }
        
        viewModel.stopOrderStatePolling()
    }
    
    @Test
    fun `loadUploadedImagesFromRepository should call repository`() = runTest {
        // Given
        val orderKey = OrderKey(12345L, 0)
        
        // When
        viewModel.loadUploadedImagesFromRepository(orderKey)
        advanceUntilIdle() // Wait for coroutine
        
        // Then
        coVerify(exactly = 1) { imageRepository.getImagesByOrderId(orderKey) }
    }

    @Test
    fun `cold order load is awaited before scheduling from the persisted start time`() = runTest {
        val order = OrderKey(12345L, 1)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } coAnswers { loaded.await() }
        val startedAt = System.currentTimeMillis() - 30_000L
        coEvery { unifiedOrderRepository.getLocalState(order) } returns
            OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = startedAt)

        val sync = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()
        verify(exactly = 0) { systemGateway.scheduleCountdownAlarm(any(), any(), any()) }

        loaded.complete(ApiResult.Success(orderInfo(order)))
        runCurrent()
        assertTrue(sync.await())
        verify(exactly = 1) {
            systemGateway.scheduleCountdownAlarm(order, "护理", match { kotlin.math.abs(it - (startedAt + 120_000L)) < 1_000L })
        }
        coVerify(exactly = 0) { unifiedOrderRepository.startLocalService(any(), any()) }
        viewModel.stopDisplayUpdates()
    }

    @Test
    fun `resume replaces the schedule after permission recovery without restarting service time`() = runTest {
        val order = OrderKey(12345L, 1)
        val startedAt = System.currentTimeMillis() - 30_000L
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } returns ApiResult.Success(orderInfo(order))
        coEvery { unifiedOrderRepository.getLocalState(order) } returns
            OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = startedAt)
        var exactAllowed = false
        val scheduledWithPermission = mutableListOf<Boolean>()
        every { systemGateway.scheduleCountdownAlarm(order, any(), any()) } answers {
            scheduledWithPermission += exactAllowed
        }

        assertTrue(viewModel.synchronizeCountdownSession(order, listOf(10)).await())
        viewModel.stopDisplayUpdates()
        exactAllowed = true
        assertTrue(viewModel.synchronizeCountdownSession(order, listOf(10)).await())

        assertEquals(listOf(false, true), scheduledWithPermission)
        verify(exactly = 2) {
            systemGateway.scheduleCountdownAlarm(order, "护理", match { kotlin.math.abs(it - (startedAt + 120_000L)) < 1_000L })
        }
        coVerify(exactly = 0) { unifiedOrderRepository.startLocalService(any(), any()) }
        viewModel.stopDisplayUpdates()
    }

    @Test
    fun `late order load cannot restart an ended business session`() = runTest {
        val order = OrderKey(12345L, 1)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } coAnswers { loaded.await() }
        val sync = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()

        viewModel.onOrderEnded(order)
        loaded.complete(ApiResult.Success(orderInfo(order)))
        runCurrent()

        assertTrue(sync.isCancelled)
        assertEquals(ServiceCountdownState.ENDED, viewModel.countdownState.value)
        verify(exactly = 0) { systemGateway.scheduleCountdownAlarm(any(), any(), any()) }
    }

    @Test
    fun `ending while persisted time is loading cannot resurrect the countdown`() = runTest {
        val order = OrderKey(12345L, 1)
        val local = CompletableDeferred<OrderLocalStateEntity?>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } returns ApiResult.Success(orderInfo(order))
        coEvery { unifiedOrderRepository.getLocalState(order) } coAnswers { local.await() }
        val sync = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()

        viewModel.onOrderEnded(order)
        local.complete(null)
        runCurrent()

        assertTrue(sync.isCancelled)
        assertEquals(ServiceCountdownState.ENDED, viewModel.countdownState.value)
        verify(exactly = 0) { systemGateway.scheduleCountdownAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { unifiedOrderRepository.startLocalService(any(), any()) }
    }

    @Test
    fun `background cancellation leaves first scheduling running with the original persisted deadline`() = runTest {
        val order = OrderKey(12345L, 1)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } coAnswers { loaded.await() }
        val startedAt = System.currentTimeMillis() - 30_000L
        coEvery { unifiedOrderRepository.getLocalState(order) } returns
            OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = startedAt)
        val sync = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()
        sync.cancel()
        viewModel.stopDisplayUpdates()
        loaded.complete(ApiResult.Success(orderInfo(order)))
        runCurrent()

        verify(exactly = 1) { systemGateway.scheduleCountdownAlarm(order, "护理", startedAt + 120_000L) }
        coVerify(exactly = 1) { unifiedOrderRepository.getLocalState(order) }
        coVerify(exactly = 0) { unifiedOrderRepository.startLocalService(any(), any()) }
        assertTrue(viewModel.synchronizeCountdownSession(order, listOf(10)).await())
        verify(exactly = 2) { systemGateway.scheduleCountdownAlarm(order, "护理", startedAt + 120_000L) }
        viewModel.stopDisplayUpdates()
    }

    @Test fun `first entry and resume share one in flight preparation`() = runTest {
        val order = OrderKey(12345L, 1)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } coAnswers { loaded.await() }
        coEvery { unifiedOrderRepository.getLocalState(order) } returns
            OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = System.currentTimeMillis())
        val first = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        val resumed = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()
        coVerify(exactly = 1) { unifiedOrderRepository.getOrderInfo(order, any()) }
        first.cancel()
        loaded.complete(ApiResult.Success(orderInfo(order)))
        runCurrent()
        assertTrue(resumed.await())
        verify(exactly = 1) { systemGateway.scheduleCountdownAlarm(order, "护理", any()) }
    }

    @Test fun `pausing during the first local read still persists and schedules without a second entry`() = runTest {
        val order = OrderKey(12345L, 1)
        val local = CompletableDeferred<OrderLocalStateEntity?>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } returns ApiResult.Success(orderInfo(order))
        coEvery { unifiedOrderRepository.getLocalState(order) } coAnswers { local.await() }
        val caller = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()
        caller.cancel()
        viewModel.stopDisplayUpdates()
        local.complete(null)
        runCurrent()
        coVerify(exactly = 1) { unifiedOrderRepository.startLocalService(order, any()) }
        verify(exactly = 1) { systemGateway.scheduleCountdownAlarm(order, "护理", any()) }
    }

    @Test fun `clearing the owning ViewModel cancels pending preparation`() = runTest {
        val order = OrderKey(12345L, 1)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } coAnswers { loaded.await() }
        val caller = backgroundScope.async { viewModel.synchronizeCountdownSession(order, listOf(10)).await() }
        runCurrent()
        clearViewModel()
        loaded.complete(ApiResult.Success(orderInfo(order)))
        runCurrent()
        assertTrue(caller.isCancelled)
        verify(exactly = 0) { systemGateway.scheduleCountdownAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { unifiedOrderRepository.startLocalService(any(), any()) }
    }

    @Test fun `a different order replaces pending preparation without borrowing its result`() = runTest {
        val firstOrder = OrderKey(12345L, 1)
        val nextOrder = OrderKey(12346L, 2)
        val loaded = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { unifiedOrderRepository.getOrderInfo(firstOrder, any()) } coAnswers { loaded.await() }
        coEvery { unifiedOrderRepository.getOrderInfo(nextOrder, any()) } returns ApiResult.Success(orderInfo(nextOrder))
        coEvery { unifiedOrderRepository.getLocalState(nextOrder) } returns
            OrderLocalStateEntity(orderId = nextOrder.orderId, localStartTimestamp = System.currentTimeMillis())
        val first = backgroundScope.async { viewModel.synchronizeCountdownSession(firstOrder, listOf(10)).await() }
        runCurrent()
        assertTrue(viewModel.synchronizeCountdownSession(nextOrder, listOf(10)).await())
        loaded.complete(ApiResult.Success(orderInfo(firstOrder)))
        runCurrent()
        assertTrue(first.isCancelled)
        verify(exactly = 0) { systemGateway.scheduleCountdownAlarm(firstOrder, any(), any()) }
        verify(exactly = 1) { systemGateway.scheduleCountdownAlarm(nextOrder, "护理", any()) }
    }

    private fun orderInfo(order: OrderKey) = ServiceOrderInfoModel(
        orderId = order.orderId,
        projectList = listOf(ServiceProjectM(projectId = 10, projectName = "护理", serviceTime = 2)),
    )

    @Test
    fun `expired countdown remains an active overtime service when resumed`() = runTest {
        val order = OrderKey(12345L, 1)
        coEvery { unifiedOrderRepository.getOrderInfo(order, any()) } returns ApiResult.Success(orderInfo(order))
        coEvery { unifiedOrderRepository.getLocalState(order) } returns
            OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = System.currentTimeMillis() - 180_000L)

        assertTrue(viewModel.synchronizeCountdownSession(order, listOf(10)).await())
        assertEquals(ServiceCountdownState.OVERTIME, viewModel.countdownState.value)
        coVerify(exactly = 0) { unifiedOrderRepository.endLocalService(any()) }
        verify(exactly = 0) { systemGateway.cancelCountdownAlarmForOrder(any()) }
        viewModel.stopDisplayUpdates()
    }
}
