package com.ytone.longcare.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartOrderNavigationContractTest {
    @Test
    fun `start order route targets nfc with complete order context`() {
        val orderParams = OrderNavParams(orderId = 987654321L, planId = 42)

        val route = startOrderNfcSignInRoute(orderParams)

        assertEquals(orderParams, route.orderParams)
        assertEquals(SignInMode.START_ORDER, route.signInMode)
        assertNull(route.endOrderParams)
    }

    @Test
    fun `start order route survives typed serialization`() {
        val expected = startOrderNfcSignInRoute(
            OrderNavParams(orderId = 73L, planId = 5),
        )

        val encoded = Json.encodeToString(NfcSignInRoute.serializer(), expected)
        val decoded = Json.decodeFromString(NfcSignInRoute.serializer(), encoded)

        assertEquals(expected, decoded)
    }
}
