package com.ytone.longcare.shared.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailErrorTest {
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val text = mockk<ResourceTextResolver>()
    private val key = OrderKey(42)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { text.text(any()) } answers { "resource:${firstArg<Int>()}" }
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } just Runs
    }

    @After fun tearDown() {
        unmockkObject(DiagnosticEventTracker)
        Dispatchers.resetMain()
    }

    @Test fun `both detail entry points map all request categories without leaking exception text`() = runTest {
        val cases = listOf(
            error(ApiRequestException.Kind.CONNECTION) to R.string.order_detail_connection_failed,
            error(ApiRequestException.Kind.TIMEOUT) to R.string.order_detail_timeout,
            error(ApiRequestException.Kind.HTTP, 503) to R.string.order_detail_server_unavailable,
            error(ApiRequestException.Kind.HTTP, 401) to R.string.order_detail_request_failed,
            error(ApiRequestException.Kind.HTTP, 404) to R.string.order_detail_request_failed,
            error(ApiRequestException.Kind.INVALID_RESPONSE) to R.string.order_detail_invalid_response,
            error(ApiRequestException.Kind.UNKNOWN) to R.string.order_detail_load_failed,
            IllegalStateException("private diagnostic text") to R.string.order_detail_load_failed,
        )
        for (shared in listOf(true, false)) {
            val page = entry(shared)
            for ((exception, expected) in cases) {
                respond(ApiResult.Exception(exception))
                page.load()
                advanceUntilIdle()
                assertEquals(OrderDetailUiState.Error("resource:$expected"), page.state.value)
            }
        }
    }

    @Test fun `business messages are preserved and blank messages have a fallback`() = runTest {
        for (shared in listOf(true, false)) {
            val page = entry(shared)
            for (message in listOf("当前计划不可执行", "", " \n ")) {
                respond(ApiResult.Failure(4001, message))
                page.load()
                advanceUntilIdle()
                assertEquals(OrderDetailUiState.Error(message.ifBlank {
                    "resource:${R.string.order_detail_load_failed}"
                }), page.state.value)
            }
        }
    }

    @Test fun `both pages transition from error through loading to fresh success`() = runTest {
        for (shared in listOf(true, false)) {
            val page = entry(shared)
            respond(ApiResult.Failure(4001, "暂不可用"))
            page.load()
            advanceUntilIdle()
            assertEquals(OrderDetailUiState.Error("暂不可用"), page.state.value)
            val fresh = ServiceOrderInfoModel(orderId = 42, state = 1)
            coEvery { details.getOrderInfo(key, any()) } coAnswers {
                kotlinx.coroutines.delay(10)
                ApiResult.Success(fresh)
            }
            coEvery { orders.getOrderInfo(key) } coAnswers {
                kotlinx.coroutines.delay(10)
                ApiResult.Success(fresh)
            }
            page.load()
            runCurrent()
            assertEquals(OrderDetailUiState.Loading, page.state.value)
            advanceUntilIdle()
            assertEquals(OrderDetailUiState.Success(fresh), page.state.value)
        }
    }

    @Test fun `cancellation is not displayed as an error or retried`() = runTest {
        coEvery { details.getOrderInfo(key, any()) } throws CancellationException()
        coEvery { orders.getOrderInfo(key) } throws CancellationException()
        for (shared in listOf(true, false)) {
            val page = entry(shared)
            page.load()
            advanceUntilIdle()
            assertFalse(page.state.value is OrderDetailUiState.Error)
        }
        coVerify(exactly = 1) { details.getOrderInfo(key, true) }
        coVerify(exactly = 1) { orders.getOrderInfo(key) }
        verify(exactly = 0) { text.text(any()) }
    }

    @Test fun `normal success is unchanged`() = runTest {
        val result = ServiceOrderInfoModel(orderId = 42)
        respond(ApiResult.Success(result))
        for (shared in listOf(true, false)) {
            val page = entry(shared)
            page.load()
            advanceUntilIdle()
            assertEquals(OrderDetailUiState.Success(result), page.state.value)
        }
    }

    private fun respond(result: ApiResult<ServiceOrderInfoModel>) {
        coEvery { details.getOrderInfo(key, any()) } returns result
        coEvery { orders.getOrderInfo(key) } returns result
    }

    @Test fun `start does not submit empty coordinates and duplicate clicks only submit once`() = runTest {
        val location = mockk<com.ytone.longcare.domain.location.LocationFacade>()
        val sample = com.ytone.longcare.model.LocationResult(31.0, 121.0, "test")
        val model = SharedOrderDetailViewModel(details, orders, location, text, mockk(relaxed = true))
        coEvery { location.acquireServiceOrderLocation() } returns
            com.ytone.longcare.domain.location.LocationAcquisition.Failure(
                com.ytone.longcare.domain.location.LocationFailure.TIMEOUT)
        model.starOrder(key)
        advanceUntilIdle()
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        assertTrue(model.starOrderState.value is StarOrderUiState.Error)

        coEvery { location.acquireServiceOrderLocation() } returns
            com.ytone.longcare.domain.location.LocationAcquisition.Success(sample)
        every { location.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        model.starOrder(key)
        model.starOrder(key)
        advanceUntilIdle()
        coVerify(exactly = 1) { orders.starOrder(key.orderId, emptyList(), "121.0", "31.0") }
        verify(exactly = 1) {
            DiagnosticEventTracker.trackEvent(any(), "service_start_location_submit", any(),
                match { it["longitude"] == 121.0 && it["latitude"] == 31.0 && it["stage"] == "service_start" }, any(), true)
        }
        verify(exactly = 1) {
            DiagnosticEventTracker.trackEvent(any(), "service_start_location_success", any(), any(), any(), true)
        }
    }

    private fun error(kind: ApiRequestException.Kind, code: Int? = null): ApiRequestException =
        mockk<ApiRequestException>().also {
            every { it.kind } returns kind
            every { it.httpCode } returns code
        }

    @Test fun `switching formal start order cancels old location result and keeps new order identity`() = runTest {
        val location = mockk<com.ytone.longcare.domain.location.LocationFacade>()
        val first = kotlinx.coroutines.CompletableDeferred<com.ytone.longcare.domain.location.LocationAcquisition>()
        val sample = com.ytone.longcare.model.LocationResult(31.0, 121.0, "test")
        coEvery { location.acquireServiceOrderLocation() } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { first.await() }
        } coAndThen { com.ytone.longcare.domain.location.LocationAcquisition.Success(sample) }
        every { location.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        val model = SharedOrderDetailViewModel(details, orders, location, text, mockk(relaxed = true))
        model.starOrder(key)
        runCurrent()
        val next = key.copy(orderId = key.orderId + 1)
        model.starOrder(next)
        runCurrent()
        first.complete(com.ytone.longcare.domain.location.LocationAcquisition.Success(sample))
        advanceUntilIdle()
        coVerify(exactly = 0) { orders.starOrder(key.orderId, any(), any(), any()) }
        coVerify(exactly = 1) { orders.starOrder(next.orderId, any(), "121.0", "31.0") }
    }

    @Test fun `late exception from cancelled SDK request does not overwrite new formal start`() = runTest {
        val location = mockk<com.ytone.longcare.domain.location.LocationFacade>()
        val first = kotlinx.coroutines.CompletableDeferred<com.ytone.longcare.domain.location.LocationAcquisition>()
        val sample = com.ytone.longcare.model.LocationResult(31.0, 121.0, "test")
        coEvery { location.acquireServiceOrderLocation() } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { first.await() }
        } coAndThen { com.ytone.longcare.domain.location.LocationAcquisition.Success(sample) }
        every { location.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        val model = SharedOrderDetailViewModel(details, orders, location, text, mockk(relaxed = true))
        model.starOrder(key); runCurrent()
        val next = key.copy(orderId = key.orderId + 1)
        model.starOrder(next); runCurrent()
        first.completeExceptionally(IllegalStateException("old SDK failed")); advanceUntilIdle()
        assertEquals(StarOrderUiState.Success(next, emptyList()), model.starOrderState.value)
        coVerify(exactly = 0) { orders.starOrder(key.orderId, any(), any(), any()) }
        coVerify(exactly = 1) { orders.starOrder(next.orderId, any(), "121.0", "31.0") }
    }

    @Test fun `cancelled acquisition can retry the same order instead of staying loading forever`() = runTest {
        val location = mockk<com.ytone.longcare.domain.location.LocationFacade>()
        coEvery { location.acquireServiceOrderLocation() } throws CancellationException()
        val lifecycle = mockk<com.ytone.longcare.domain.order.ServiceOrderLifecycle>(relaxed = true)
        val model = SharedOrderDetailViewModel(details, orders, location, text, lifecycle)

        model.starOrder(key)
        runCurrent()
        assertEquals(StarOrderUiState.Initial, model.starOrderState.value)
        model.starOrder(key)
        runCurrent()

        coVerify(exactly = 2) { location.acquireServiceOrderLocation() }
        verify(exactly = 2) { lifecycle.prepareOrderStart(key.orderId) }
        verify(exactly = 2) { lifecycle.cancelOrderPreparation(key.orderId) }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
    }

    @Test fun `late cancelled preparation cannot release a new preparation for the same order`() = runTest {
        val location = mockk<com.ytone.longcare.domain.location.LocationFacade>()
        val oldResult = kotlinx.coroutines.CompletableDeferred<com.ytone.longcare.domain.location.LocationAcquisition>()
        val newResult = kotlinx.coroutines.CompletableDeferred<com.ytone.longcare.domain.location.LocationAcquisition>()
        coEvery { location.acquireServiceOrderLocation() } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { oldResult.await() }
        } coAndThen { newResult.await() }
        val lifecycle = mockk<com.ytone.longcare.domain.order.ServiceOrderLifecycle>(relaxed = true)
        val model = SharedOrderDetailViewModel(details, orders, location, text, lifecycle)
        model.starOrder(key)
        runCurrent()
        val otherOrder = key.copy(orderId = key.orderId + 1)
        coEvery { details.getOrderInfo(otherOrder, any()) } returns ApiResult.Success(
            ServiceOrderInfoModel(orderId = otherOrder.orderId),
        )
        model.getOrderInfo(otherOrder)
        model.starOrder(key)
        runCurrent()
        verify(exactly = 1) { lifecycle.cancelOrderPreparation(key.orderId) }

        oldResult.complete(com.ytone.longcare.domain.location.LocationAcquisition.Failure(
            com.ytone.longcare.domain.location.LocationFailure.TIMEOUT))
        runCurrent()

        verify(exactly = 1) { lifecycle.cancelOrderPreparation(key.orderId) }
        assertEquals(StarOrderUiState.Loading, model.starOrderState.value)
        newResult.complete(com.ytone.longcare.domain.location.LocationAcquisition.Failure(
            com.ytone.longcare.domain.location.LocationFailure.TIMEOUT))
        runCurrent()
        verify(exactly = 2) { lifecycle.cancelOrderPreparation(key.orderId) }
    }

    private data class Entry(val state: StateFlow<OrderDetailUiState>, val load: () -> Unit)

    private fun entry(shared: Boolean): Entry = if (shared) {
        val vm = SharedOrderDetailViewModel(details, orders, mockk(), text, mockk(relaxed = true))
        Entry(vm.uiState) { vm.getOrderInfo(key, forceRefresh = true) }
    } else {
        val vm = OrderDetailViewModel(orders, details, text)
        Entry(vm.uiState) { vm.getOrderInfo(key) }
    }
}
