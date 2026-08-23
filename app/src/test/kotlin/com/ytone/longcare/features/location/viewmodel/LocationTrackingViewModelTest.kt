package com.ytone.longcare.features.location.viewmodel

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.model.OrderKey
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LocationTrackingViewModelTest {
    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `startTracking delegates the order key`() {
        val manager = mockk<LocationTrackingManager>(relaxed = true)
        val viewModel = LocationTrackingViewModel(manager)
        val orderKey = OrderKey(orderId = 123L, planId = 1)

        viewModel.startTracking(orderKey)

        verify(exactly = 1) { manager.startTracking(orderKey) }
    }

    @Test
    fun `permission grant start delegates as one operation`() {
        val manager = mockk<LocationTrackingManager>(relaxed = true)
        val viewModel = LocationTrackingViewModel(manager)
        val orderKey = OrderKey(orderId = 123L, planId = 1)

        viewModel.startTrackingAfterPermissionGrant(orderKey)

        verify(exactly = 1) { manager.startTrackingAfterPermissionGrant(orderKey) }
    }

    @Test
    fun `stopTracking delegates once`() {
        val manager = mockk<LocationTrackingManager>(relaxed = true)
        val viewModel = LocationTrackingViewModel(manager)

        viewModel.stopTracking()

        verify(exactly = 1) { manager.stopTracking() }
    }
}
