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
    fun extractOrderKey_shouldUseNewKeyWhenPresent() {
        val expected = OrderKey(orderId = 1001L, planId = 11)
        val intent = Intent().apply {
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, expected)
        }

        assertEquals(expected, CountdownNotificationManager.extractOrderKey(intent))
    }

    @Test
    fun extractOrderKey_shouldFallbackToLegacyKey() {
        val expected = OrderKey(orderId = 1002L, planId = 22)
        val intent = Intent().apply {
            putExtra("extra_request", expected)
        }

        assertEquals(expected, CountdownNotificationManager.extractOrderKey(intent))
    }

    @Test
    fun extractOrderKey_shouldPreferNewKeyOverLegacyKey() {
        val expected = OrderKey(orderId = 1003L, planId = 33)
        val legacy = OrderKey(orderId = 9999L, planId = 99)
        val intent = Intent().apply {
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, expected)
            putExtra("extra_request", legacy)
        }

        assertEquals(expected, CountdownNotificationManager.extractOrderKey(intent))
    }

    @Test
    fun extractOrderKey_shouldReturnDefaultWhenMissing() {
        val defaultValue = OrderKey(orderId = 2001L, planId = 1)

        assertEquals(defaultValue, CountdownNotificationManager.extractOrderKey(Intent(), defaultValue))
        assertEquals(defaultValue, CountdownNotificationManager.extractOrderKey(null, defaultValue))
    }

    @Test
    fun extractServiceName_shouldUseNewKeyWhenPresent() {
        val intent = Intent().apply {
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, "新服务名")
        }

        assertEquals(
            "新服务名",
            CountdownNotificationManager.extractServiceName(intent, "默认服务")
        )
    }

    @Test
    fun extractServiceName_shouldFallbackToLegacyKey() {
        val intent = Intent().apply {
            putExtra("service_name", "旧服务名")
        }

        assertEquals(
            "旧服务名",
            CountdownNotificationManager.extractServiceName(intent, "默认服务")
        )
    }

    @Test
    fun extractServiceName_shouldPreferNewKeyOverLegacyKey() {
        val intent = Intent().apply {
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, "新服务名")
            putExtra("service_name", "旧服务名")
        }

        assertEquals(
            "新服务名",
            CountdownNotificationManager.extractServiceName(intent, "默认服务")
        )
    }

    @Test
    fun extractServiceName_shouldReturnDefaultWhenMissing() {
        assertEquals(
            "默认服务",
            CountdownNotificationManager.extractServiceName(Intent(), "默认服务")
        )
        assertEquals(
            "默认服务",
            CountdownNotificationManager.extractServiceName(null, "默认服务")
        )
    }
}
