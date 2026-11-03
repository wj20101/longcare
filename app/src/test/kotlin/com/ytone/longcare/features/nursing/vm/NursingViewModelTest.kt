package com.ytone.longcare.features.nursing.vm

import com.ytone.longcare.core.ui.message.UiText
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import com.ytone.longcare.core.ui.R as CoreUiR

@OptIn(ExperimentalCoroutinesApi::class)
class NursingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<OrderRepository>()
    private val firstOrders = listOf(ServiceOrderModel(orderId = 1))
    private val secondOrders = listOf(ServiceOrderModel(orderId = 2))

    @Test
    fun `late result from an old date cannot replace the selected date`() = runTest {
        val oldResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        coEvery { repository.getOrderList("2026-11-03") } coAnswers {
            withContext(NonCancellable) { oldResult.await() }
        }
        coEvery { repository.getOrderList("2026-11-04") } returns ApiResult.Success(secondOrders)
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        viewModel.getOrderList("2026-11-04")
        advanceUntilIdle()
        oldResult.complete(ApiResult.Success(firstOrders))
        advanceUntilIdle()

        assertEquals(NursingOrderListState("2026-11-04", secondOrders), viewModel.orderListState.value)
    }

    @Test
    fun `late failure cannot notify or clear the new request loading state`() = runTest {
        val oldResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        val newResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        coEvery { repository.getOrderList("2026-11-03") } coAnswers {
            withContext(NonCancellable) { oldResult.await() }
        }
        coEvery { repository.getOrderList("2026-11-04") } coAnswers { newResult.await() }
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        viewModel.getOrderList("2026-11-04")
        oldResult.complete(ApiResult.Failure(500, "old request failed"))
        advanceUntilIdle()

        assertEquals(NursingOrderListState(daytime = "2026-11-04", isLoading = true), viewModel.orderListState.value)
        assertTrue(viewModel.uiMessages.value.isEmpty())

        newResult.complete(ApiResult.Success(secondOrders))
        advanceUntilIdle()
        assertEquals(NursingOrderListState("2026-11-04", secondOrders), viewModel.orderListState.value)
    }

    @Test
    fun `selecting a new date cancels the previous request`() = runTest {
        var cancelled = false
        val newResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        coEvery { repository.getOrderList("2026-11-03") } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        coEvery { repository.getOrderList("2026-11-04") } coAnswers { newResult.await() }
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        viewModel.getOrderList("2026-11-04")
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(NursingOrderListState(daytime = "2026-11-04", isLoading = true), viewModel.orderListState.value)
        newResult.complete(ApiResult.Success(secondOrders))
        advanceUntilIdle()
    }

    @Test
    fun `same date can be refreshed after a completed request`() = runTest {
        coEvery { repository.getOrderList("2026-11-03") } returnsMany listOf(
            ApiResult.Success(firstOrders), ApiResult.Success(secondOrders),
        )
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        advanceUntilIdle()
        assertEquals(firstOrders, viewModel.orderListState.value.orders)
        viewModel.getOrderList("2026-11-03")
        advanceUntilIdle()

        assertEquals(NursingOrderListState("2026-11-03", secondOrders), viewModel.orderListState.value)
        coVerify(exactly = 2) { repository.getOrderList("2026-11-03") }
    }

    @Test
    fun `refresh of the same date also rejects an older result`() = runTest {
        val oldResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        var calls = 0
        coEvery { repository.getOrderList("2026-11-03") } coAnswers {
            if (calls++ == 0) withContext(NonCancellable) { oldResult.await() }
            else ApiResult.Success(secondOrders)
        }
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        viewModel.getOrderList("2026-11-03")
        advanceUntilIdle()
        oldResult.complete(ApiResult.Success(firstOrders))
        advanceUntilIdle()

        assertEquals(NursingOrderListState("2026-11-03", secondOrders), viewModel.orderListState.value)
    }

    @Test
    fun `current business failure clears old orders and retains an acknowledged message`() = runTest {
        val newResult = CompletableDeferred<ApiResult<List<ServiceOrderModel>>>()
        coEvery { repository.getOrderList("2026-11-03") } returns ApiResult.Success(firstOrders)
        coEvery { repository.getOrderList("2026-11-04") } coAnswers { newResult.await() }
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        advanceUntilIdle()
        viewModel.getOrderList("2026-11-04")
        assertEquals(NursingOrderListState(daytime = "2026-11-04", isLoading = true), viewModel.orderListState.value)
        newResult.complete(ApiResult.Failure(4001, "无法查询护理计划"))
        advanceUntilIdle()

        assertEquals(NursingOrderListState("2026-11-04"), viewModel.orderListState.value)
        val message = viewModel.uiMessages.value.single()
        assertEquals(UiText.Dynamic("无法查询护理计划"), message.content)
        viewModel.consumeUiMessage(message.id)
        assertTrue(viewModel.uiMessages.value.isEmpty())
    }

    @Test
    fun `current network exception ends loading and shows the existing retry message`() = runTest {
        coEvery { repository.getOrderList("2026-11-03") } returns ApiResult.Exception(IOException("offline"))
        val viewModel = NursingViewModel(repository)

        viewModel.getOrderList("2026-11-03")
        advanceUntilIdle()

        assertEquals(NursingOrderListState("2026-11-03"), viewModel.orderListState.value)
        assertEquals(UiText.Resource(CoreUiR.string.common_network_error_retry), viewModel.uiMessages.value.single().content)
    }

    @Test
    fun `cancellation remains cancellation and does not show a network failure`() = runTest {
        for (wrapped in listOf(false, true)) {
            lateinit var requestJob: Job
            val cancellation = CancellationException("cancelled")
            coEvery { repository.getOrderList("2026-11-03") } coAnswers {
                requestJob = currentCoroutineContext().job
                if (wrapped) ApiResult.Exception(cancellation) else throw cancellation
            }
            val viewModel = NursingViewModel(repository)

            viewModel.getOrderList("2026-11-03")
            advanceUntilIdle()

            assertTrue(requestJob.isCancelled)
            assertFalse(viewModel.orderListState.value.isLoading)
            assertTrue(viewModel.uiMessages.value.isEmpty())
        }
    }
}
