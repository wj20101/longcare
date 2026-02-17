package com.ytone.longcare.features.location.viewmodel

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.common.utils.KLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class LocationTrackingViewModelTest {

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `state flows should be delegated from manager`() {
        val isTrackingFlow = MutableStateFlow(false)
        val orderKeyFlow = MutableStateFlow<OrderKey?>(null)
        val manager = mockManager(isTrackingFlow, orderKeyFlow)

        val viewModel = LocationTrackingViewModel(manager)

        assertSame(isTrackingFlow, viewModel.isTracking)
        assertSame(orderKeyFlow, viewModel.currentTrackingOrderKey)
    }

    @Test
    fun `onStartClicked should delegate startTracking with orderKey`() {
        val manager = mockManager()
        val viewModel = LocationTrackingViewModel(manager)
        val orderKey = OrderKey(orderId = 123L, planId = 1)

        viewModel.onStartClicked(orderKey)

        verify(exactly = 1) { manager.startTracking(orderKey) }
    }

    @Test
    fun `ensureLocationSessionForOrder should stop old tracking when tracking another order`() {
        val manager = mockManager(
            isTrackingFlow = MutableStateFlow(true),
            orderKeyFlow = MutableStateFlow(OrderKey(orderId = 200L, planId = 0))
        )
        val viewModel = LocationTrackingViewModel(manager)

        viewModel.ensureLocationSessionForOrder(orderId = 100L)

        verify(exactly = 1) { manager.stopTracking() }
        verify(exactly = 1) { manager.startLocationSession() }
    }

    @Test
    fun `ensureLocationSessionForOrder should not stop tracking when same order`() {
        val manager = mockManager(
            isTrackingFlow = MutableStateFlow(true),
            orderKeyFlow = MutableStateFlow(OrderKey(orderId = 100L, planId = 9))
        )
        val viewModel = LocationTrackingViewModel(manager)

        viewModel.ensureLocationSessionForOrder(orderId = 100L)

        verify(exactly = 0) { manager.stopTracking() }
        verify(exactly = 1) { manager.startLocationSession() }
    }

    @Test
    fun `onStopClicked should delegate stopTracking`() {
        val manager = mockManager()
        val viewModel = LocationTrackingViewModel(manager)

        viewModel.onStopClicked()

        verify(exactly = 1) { manager.stopTracking() }
    }

    @Test
    fun `forceStop should delegate forceStopTracking`() {
        val manager = mockManager()
        val viewModel = LocationTrackingViewModel(manager)

        viewModel.forceStop()

        verify(exactly = 1) { manager.forceStopTracking() }
    }

    private fun mockManager(
        isTrackingFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        orderKeyFlow: MutableStateFlow<OrderKey?> = MutableStateFlow(null)
    ): LocationTrackingManager {
        return mockk(relaxed = true) {
            every { isTracking } returns isTrackingFlow
            every { currentTrackingOrderKey } returns orderKeyFlow
        }
    }
}
