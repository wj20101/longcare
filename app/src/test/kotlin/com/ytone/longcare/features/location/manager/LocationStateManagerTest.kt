package com.ytone.longcare.features.location.manager

import com.ytone.longcare.model.OrderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStateManagerTest {

    @Test
    fun `startTracking records active order and start time`() {
        val manager = LocationStateManager()
        val orderKey = OrderKey(orderId = 100L, planId = 0)

        manager.startTracking(orderKey)

        val state = manager.state.value
        assertTrue(state.isTracking)
        assertEquals(100L, state.currentOrderId)
        assertNotNull(state.startTime)
        assertNotNull(manager.getRunningDuration())
    }

    @Test
    fun `stopTracking clears active order and start time`() {
        val manager = LocationStateManager()
        manager.startTracking(OrderKey(orderId = 100L, planId = 0))

        manager.stopTracking()

        val state = manager.state.value
        assertFalse(state.isTracking)
        assertEquals(null, state.currentOrderId)
        assertEquals(null, state.startTime)
        assertEquals(null, manager.getRunningDuration())
    }
}
