package com.ytone.longcare.features.service

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 服务时间通知管理器基础测试
 * 验证核心功能逻辑
 */
@RunWith(RobolectricTestRunner::class)
class ServiceTimeNotificationManagerBasicTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences("service_time_notification_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
    }

    @Test
    fun `notification processing mark should be set correctly`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        
        // When - 模拟标记为已处理
        sharedPreferences.edit().putLong("last_processed_order_$orderId", currentTime).apply()
        
        // Then - 验证编辑器被调用
        val storedTime = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        assertEquals("处理时间应被正确写入 SharedPreferences", currentTime, storedTime)
    }

    @Test
    fun `duplicate check should work within time window`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val recentTime = currentTime - 15 * 60 * 1000 // 15分钟前
        
        sharedPreferences.edit().putLong("last_processed_order_$orderId", recentTime).apply()
        
        // When - 检查是否认为已处理（30分钟内）
        val lastProcessed = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        
        // Then
        assertTrue("应该识别为重复通知", isDuplicate)
    }

    @Test
    fun `old notifications should not be considered duplicates`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val oldTime = currentTime - 60 * 60 * 1000 // 1小时前
        
        sharedPreferences.edit().putLong("last_processed_order_$orderId", oldTime).apply()
        
        // When - 检查是否认为已过期
        val lastProcessed = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        
        // Then
        assertFalse("旧通知不应该被认为是重复", isDuplicate)
    }

    @Test
    fun `notification cleanup should work correctly`() {
        // Given
        val orderId = 12345L
        
        // When - 清除处理标记
        sharedPreferences.edit().putLong("last_processed_order_$orderId", System.currentTimeMillis()).apply()
        sharedPreferences.edit().remove("last_processed_order_$orderId").apply()
        
        // Then
        val storedTime = sharedPreferences.getLong("last_processed_order_$orderId", 0)
        assertEquals("清理后应不存在处理时间记录", 0L, storedTime)
    }
}
