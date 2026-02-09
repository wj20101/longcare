package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.data.database.entity.OrderEntity
import com.ytone.longcare.data.database.entity.OrderLocalStateEntity
import com.ytone.longcare.data.database.entity.OrderProjectEntity
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.repository.ImageRepository
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ServiceCountdownViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var unifiedOrderRepository: UnifiedOrderRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var countdownNotificationManager: CountdownNotificationManager
    private lateinit var toastHelper: ToastHelper
    private lateinit var viewModel: ServiceCountdownViewModel

    @Before
    fun setup() {
        unifiedOrderRepository = mockk(relaxed = true)
        imageRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        countdownNotificationManager = mockk(relaxed = true)
        toastHelper = mockk(relaxed = true)
        
        // Mock default flows
        every { unifiedOrderRepository.observeOrderWithDetails(any()) } returns MutableStateFlow(null)
        
        viewModel = ServiceCountdownViewModel(
            toastHelper,
            unifiedOrderRepository,
            imageRepository,
            orderRepository,
            countdownNotificationManager
        )
    }

    @Test
    fun `startOrderStatePolling should call repository`() = runTest {
        // Given
        val request = OrderInfoRequestModel(orderId = 12345L, planId = 1)
        
        // When
        viewModel.startOrderStatePolling(request.toOrderKey())
        
        // Polling starts with a delay (5s). Advance time to trigger loop body.
        advanceTimeBy(5100L)
        
        // This confirms the method runs without crashing and uses OrderKey
        coVerify(atLeast = 1) { orderRepository.getOrderState(12345L) }
        
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
}
