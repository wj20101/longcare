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
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any()) } just Runs
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

    private fun error(kind: ApiRequestException.Kind, code: Int? = null): ApiRequestException =
        mockk<ApiRequestException>().also {
            every { it.kind } returns kind
            every { it.httpCode } returns code
        }

    private data class Entry(val state: StateFlow<OrderDetailUiState>, val load: () -> Unit)

    private fun entry(shared: Boolean): Entry = if (shared) {
        val vm = SharedOrderDetailViewModel(details, orders, mockk(), mockk(), text)
        Entry(vm.uiState) { vm.getOrderInfo(key, forceRefresh = true) }
    } else {
        val vm = OrderDetailViewModel(orders, details, text)
        Entry(vm.uiState) { vm.getOrderInfo(key) }
    }
}
