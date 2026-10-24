package com.ytone.longcare.shared.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailRequestIsolationTest {
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val key = OrderKey(42)

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `late result from replaced order cannot publish after the current order`() = runTest {
        val old = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { details.getOrderInfo(key, any()) } coAnswers {
            withContext(NonCancellable) { old.await() }
        }
        val next = OrderKey(43)
        val fresh = ServiceOrderInfoModel(orderId = 43)
        coEvery { details.getOrderInfo(next, any()) } returns ApiResult.Success(fresh)
        val model = model()
        model.getOrderInfo(key)
        runCurrent()
        model.getOrderInfo(next)
        runCurrent()
        old.complete(ApiResult.Failure(500, "old error"))
        runCurrent()

        assertEquals(OrderDetailUiState.Success(fresh), model.uiState.value)
    }

    @Test fun `same order request is reused but force refresh cancels the old result`() = runTest {
        val old = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        val fresh = ServiceOrderInfoModel(orderId = 42, state = 1)
        coEvery { details.getOrderInfo(key, false) } coAnswers {
            withContext(NonCancellable) { old.await() }
        }
        coEvery { details.getOrderInfo(key, true) } returns ApiResult.Success(fresh)
        val model = model()
        model.getOrderInfo(key)
        runCurrent()
        model.getOrderInfo(key)
        runCurrent()
        coVerify(exactly = 1) { details.getOrderInfo(key, false) }
        model.getOrderInfo(key, forceRefresh = true)
        runCurrent()
        old.complete(ApiResult.Success(ServiceOrderInfoModel(orderId = 42, state = 0)))
        runCurrent()

        assertEquals(OrderDetailUiState.Success(fresh), model.uiState.value)
    }

    private fun model() = SharedOrderDetailViewModel(
        details, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true),
    )
}
