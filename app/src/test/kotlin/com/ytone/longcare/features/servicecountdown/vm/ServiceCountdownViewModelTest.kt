package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.model.OrderEntity
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.OrderProjectEntity
import com.ytone.longcare.data.repository.ImageRepository
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
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
        every { unifiedOrderRepository.observeOrderWithDetails(any()) } returns MutableStateFlow(null)
        
        viewModel = ServiceCountdownViewModel(
            unifiedOrderRepository,
            imageRepository,
            serviceOrderLifecycle,
            systemGateway
        )
    }

    @Test
    fun `ending service stops platform work through gateway`() = runTest {
        val orderKey = OrderKey(orderId = 12345L, planId = 1)

        viewModel.endServiceWithoutClearingImages(orderKey)
        advanceUntilIdle()

        verify(exactly = 1) { systemGateway.stopForegroundService() }
        verify(exactly = 1) { systemGateway.stopAlarmRingtone() }
        verify(exactly = 1) { systemGateway.cancelCountdownAlarmForOrder(orderKey) }
        coVerify(exactly = 1) { unifiedOrderRepository.endLocalService(orderKey) }
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
}
