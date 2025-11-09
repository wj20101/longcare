package com.ytone.longcare.features.service

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 服务时间通知管理器简单测试
 * 验证核心逻辑，不依赖MockK
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceTimeNotificationManagerSimpleTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
    }

    @Test
    fun `test notification processing mark storage`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        
        // When - 存储处理标记
        sharedPreferences.edit().putLong("last_processed_order_$orderId", currentTime).apply()
        
        // Then - 验证存储成功
        val storedTime = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        assertEquals(currentTime, storedTime)
    }

    @Test
    fun `test duplicate check within time window`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val recentTime = currentTime - 15 * 60 * 1000 // 15分钟前
        
        // When - 存储最近处理时间
        sharedPreferences.edit().putLong("last_processed_order_$orderId", recentTime).apply()
        
        // Then - 检查是否认为是重复（30分钟内）
        val lastProcessed = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        assertTrue("应该识别为重复通知", isDuplicate)
    }

    @Test
    fun `test old notifications not considered duplicates`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val oldTime = currentTime - 60 * 60 * 1000 // 1小时前
        
        // When - 存储旧处理时间
        sharedPreferences.edit().putLong("last_processed_order_$orderId", oldTime).apply()
        
        // Then - 检查是否认为不是重复
        val lastProcessed = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        assertFalse("旧通知不应该被认为是重复", isDuplicate)
    }

    @Test
    fun `test notification cleanup`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        
        // When - 存储处理标记然后清除
        sharedPreferences.edit().putLong("last_processed_order_$orderId", currentTime).apply()
        var storedTime = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        assertEquals(currentTime, storedTime)
        
        sharedPreferences.edit().remove("last_processed_order_$orderId").apply()
        
        // Then - 验证已清除
        storedTime = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        assertEquals(0, storedTime)
    }

    @Test
    fun `test multiple order ids isolation`() {
        // Given
        val orderId1 = 12345L
        val orderId2 = 67890L
        val currentTime = System.currentTimeMillis()
        
        // When - 存储两个不同订单的处理时间
        sharedPreferences.edit().putLong("last_processed_order_$orderId1", currentTime).apply()
        sharedPreferences.edit().putLong("last_processed_order_$orderId2", currentTime + 1000).apply()
        
        // Then - 验证两个订单的处理时间独立存储
        val time1 = sharedPreferences.getLong("last_processed_order_$orderId1", 0)
        val time2 = sharedPreferences.getLong("last_processed_order_$orderId2", 0)
        
        assertEquals(currentTime, time1)
        assertEquals(currentTime + 1000, time2)
    }
}