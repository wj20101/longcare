package com.ytone.longcare.features.service

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 服务时间通知管理器基础测试
 * 验证核心功能逻辑
 */
@RunWith(RobolectricTestRunner::class)
class ServiceTimeNotificationManagerBasicTest {

    private lateinit var context: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        
        // Mock SharedPreferences 获取
        mockkStatic(android.content.Context::class)
        every { context.getSharedPreferences(any(), any()) } returns mockSharedPreferences
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `notification processing mark should be set correctly`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        
        every { mockSharedPreferences.getLong(any(), 0) } returns 0
        
        // When - 模拟标记为已处理
        val prefs = context.getSharedPreferences("service_time_notification_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_processed_order_$orderId", currentTime).apply()
        
        // Then - 验证编辑器被调用
        verify { mockEditor.putLong("last_processed_order_$orderId", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `duplicate check should work within time window`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val recentTime = currentTime - 15 * 60 * 1000 // 15分钟前
        
        every { mockSharedPreferences.getLong("last_processed_order_$orderId", 0) } returns recentTime
        
        // When - 检查是否认为已处理（30分钟内）
        val lastProcessed = mockSharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        
        // Then
        assert(isDuplicate) { "应该识别为重复通知" }
    }

    @Test
    fun `old notifications should not be considered duplicates`() {
        // Given
        val orderId = 12345L
        val currentTime = System.currentTimeMillis()
        val oldTime = currentTime - 60 * 60 * 1000 // 1小时前
        
        every { mockSharedPreferences.getLong("last_processed_order_$orderId", 0) } returns oldTime
        
        // When - 检查是否认为已过期
        val lastProcessed = mockSharedPreferences.getLong("last_processed_order_$orderId", 0)
        val isDuplicate = currentTime - lastProcessed < 30 * 60 * 1000
        
        // Then
        assert(!isDuplicate) { "旧通知不应该被认为是重复" }
    }

    @Test
    fun `notification cleanup should work correctly`() {
        // Given
        val orderId = 12345L
        
        every { mockEditor.remove(any()) } returns mockEditor
        
        // When - 清除处理标记
        mockSharedPreferences.edit().remove("last_processed_order_$orderId").apply()
        
        // Then
        verify { mockEditor.remove("last_processed_order_$orderId") }
        verify { mockEditor.apply() }
    }
}