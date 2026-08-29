package com.ytone.longcare.features.countdown.manager

import android.content.Intent
import com.ytone.longcare.model.OrderKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CountdownNotificationManagerIntentExtrasTest {

    @Test
    fun `canonical activity extras round trip`() {
        val expected = OrderKey(orderId = 1001L, planId = 11)
        val intent = Intent().apply {
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, expected)
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, "新服务名")
        }

        assertEquals(expected, CountdownNotificationManager.extractOrderKey(intent))
        assertEquals(
            "新服务名",
            CountdownNotificationManager.extractServiceName(intent, "默认服务"),
        )
    }

    @Test
    fun `legacy extras are not restored`() {
        val defaultOrder = OrderKey(orderId = 2001L, planId = 1)
        val intent = Intent().apply {
            putExtra("extra_request", OrderKey(9999, 99))
            putExtra("service_name", "旧服务名")
        }

        assertEquals(defaultOrder, CountdownNotificationManager.extractOrderKey(intent, defaultOrder))
        assertEquals("默认服务", CountdownNotificationManager.extractServiceName(intent, "默认服务"))
    }
}
