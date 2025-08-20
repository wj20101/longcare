package com.ytone.longcare.features.servicecountdown.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Singleton
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.debug.CountdownNotificationDebugHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 倒计时通知测试工具
 * 用于测试和验证倒计时通知功能
 */
@Singleton
class CountdownNotificationTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val countdownNotificationManager: CountdownNotificationManager,
    private val debugHelper: CountdownNotificationDebugHelper
) {
    
    companion object {
        private const val TAG = "CountdownNotificationTester"
        private const val TEST_ORDER_ID = 999999L
        private const val TEST_SERVICE_NAME = "测试服务"
    }
    
    /**
     * 执行完整的通知功能测试
     */
    fun runFullTest() {
        Log.i(TAG, "开始执行倒计时通知功能测试")
        
        // 1. 诊断系统状态
        Log.i(TAG, "=== 步骤1: 诊断系统状态 ===")
        debugHelper.diagnoseNotificationIssues(context)
        
        // 2. 测试权限状态
        Log.i(TAG, "=== 步骤2: 检查权限状态 ===")
        testPermissions()
        
        // 3. 测试通知渠道
        Log.i(TAG, "=== 步骤3: 测试通知渠道 ===")
        testNotificationChannel()
        
        // 4. 测试短时间闹钟（30秒后触发）
        Log.i(TAG, "=== 步骤4: 测试短时间闹钟 ===")
        testShortAlarm()
        
        Log.i(TAG, "倒计时通知功能测试完成，请等待30秒观察通知是否触发")
    }
    
    /**
     * 测试权限状态
     */
    private fun testPermissions() {
        val canScheduleAlarms = countdownNotificationManager.canScheduleExactAlarms()
        Log.i(TAG, "精确闹钟权限: ${if (canScheduleAlarms) "已授予" else "未授予"}")
        
        if (!canScheduleAlarms) {
            Log.w(TAG, "警告: 精确闹钟权限未授予，可能影响通知准时性")
        }
    }
    
    /**
     * 测试通知渠道
     */
    private fun testNotificationChannel() {
        try {
            // 显示一个测试通知
            countdownNotificationManager.showCountdownCompletionNotification(
                orderId = TEST_ORDER_ID,
                serviceName = TEST_SERVICE_NAME
            )
            Log.i(TAG, "测试通知已发送")
        } catch (e: Exception) {
            Log.e(TAG, "发送测试通知失败: ${e.message}", e)
        }
    }
    
    /**
     * 测试短时间闹钟
     */
    private fun testShortAlarm() {
        try {
            // 设置30秒后触发的闹钟
            val triggerTime = System.currentTimeMillis() + 30 * 1000L
            
            countdownNotificationManager.scheduleCountdownAlarm(
                orderId = TEST_ORDER_ID,
                serviceName = TEST_SERVICE_NAME,
                triggerTimeMillis = triggerTime
            )
            
            Log.i(TAG, "测试闹钟已设置，将在30秒后触发")
            Log.i(TAG, "触发时间: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        } catch (e: Exception) {
            Log.e(TAG, "设置测试闹钟失败: ${e.message}", e)
        }
    }
    
    /**
     * 取消测试闹钟
     */
    fun cancelTestAlarm() {
        try {
            countdownNotificationManager.cancelCountdownAlarm()
            Log.i(TAG, "测试闹钟已取消")
        } catch (e: Exception) {
            Log.e(TAG, "取消测试闹钟失败: ${e.message}", e)
        }
    }
    
    /**
     * 立即触发测试通知
     */
    fun triggerTestNotification() {
        try {
            countdownNotificationManager.showCountdownCompletionNotification(
                orderId = TEST_ORDER_ID,
                serviceName = TEST_SERVICE_NAME
            )
            Log.i(TAG, "立即触发测试通知成功")
        } catch (e: Exception) {
            Log.e(TAG, "立即触发测试通知失败: ${e.message}", e)
        }
    }
}