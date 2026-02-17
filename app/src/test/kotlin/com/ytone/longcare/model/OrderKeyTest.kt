package com.ytone.longcare.model

import com.ytone.longcare.navigation.OrderNavParams
import com.ytone.longcare.navigation.toOrderKey
import com.ytone.longcare.navigation.toOrderNavParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderKeyTest {

    @Test
    fun `test OrderKey creation and cacheKey`() {
        val orderKey = OrderKey(orderId = 12345L, planId = 1)
        
        assertEquals(12345L, orderKey.orderId)
        assertEquals(1, orderKey.planId)
        assertEquals("12345_1", orderKey.cacheKey)
    }

    @Test
    fun `test OrderKey default planId`() {
        val orderKey = OrderKey(orderId = 67890L)
        
        assertEquals(67890L, orderKey.orderId)
        assertEquals(0, orderKey.planId)
        assertEquals("67890_0", orderKey.cacheKey)
    }

    @Test
    fun `test OrderNavParams toOrderKey extension`() {
        val navParams = OrderNavParams(orderId = 111L, planId = 2)
        val orderKey = navParams.toOrderKey()
        
        assertEquals(111L, orderKey.orderId)
        assertEquals(2, orderKey.planId)
    }

    @Test
    fun `test OrderKey toOrderNavParams extension`() {
        val orderKey = OrderKey(orderId = 222L, planId = 3)
        val navParams = orderKey.toOrderNavParams()
        
        assertEquals(222L, navParams.orderId)
        assertEquals(3, navParams.planId)
    }

    @Test
    fun `test OrderInfoRequestModel toOrderKey extension`() {
        val requestModel = OrderInfoRequestModel(orderId = 333L, planId = 4)
        val orderKey = requestModel.toOrderKey()
        
        assertEquals(333L, orderKey.orderId)
        assertEquals(4, orderKey.planId)
    }

    @Test
    fun `test OrderKey toRequestModel extension`() {
        val orderKey = OrderKey(orderId = 444L, planId = 5)
        val requestModel = orderKey.toRequestModel()
        
        assertEquals(444L, requestModel.orderId)
        assertEquals(5, requestModel.planId)
    }

    @Test
    fun `test handleOrderNavigation with OrderKey callback routes pending care to nursing`() {
        val orderKey = OrderKey(orderId = 555L, planId = 6)
        var nursingTarget: OrderKey? = null
        var serviceTarget: OrderKey? = null

        handleOrderNavigation(
            state = 0,
            orderId = orderKey.orderId,
            planId = orderKey.planId,
            onNavigateToNursingExecution = { nursingTarget = it },
            onNavigateToService = { serviceTarget = it }
        )

        assertEquals(orderKey, nursingTarget)
        assertNull(serviceTarget)
    }

    @Test
    fun `test handleOrderNavigation with OrderKey callback routes completed to service`() {
        val orderKey = OrderKey(orderId = 666L, planId = 7)
        var nursingTarget: OrderKey? = null
        var serviceTarget: OrderKey? = null

        handleOrderNavigation(
            state = 2,
            orderId = orderKey.orderId,
            planId = orderKey.planId,
            onNavigateToNursingExecution = { nursingTarget = it },
            onNavigateToService = { serviceTarget = it }
        )

        assertNull(nursingTarget)
        assertEquals(orderKey, serviceTarget)
    }
}
