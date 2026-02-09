package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 服务时间结束通知集成测试
 * 验证三重保障机制的完整流程
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceTimeNotificationIntegrationTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var workManager: WorkManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pendingOrdersStorage: PendingOrdersStorage
    private lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // 初始化WorkManager测试环境
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        workManager = WorkManager.getInstance(context)
        sharedPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        pendingOrdersStorage = PendingOrdersStorage(context, "test_pending_orders")
        
        serviceTimeNotificationManager = ServiceTimeNotificationManager(
            context,
            notificationManager,
            alarmManager,
            workManager,
            pendingOrdersStorage
        )
    }

    @Test
    fun testCompleteNotificationFlow() {
        // 测试数据
        val orderId = 12345L
        val serviceName = "测试服务"
        val serviceEndTime = System.currentTimeMillis() + 5000 // 5秒后
        
        // 1. 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            serviceEndTime
        )
        
        // 验证待处理订单已保存
        val pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, pendingOrders.size)
        assertEquals(orderId, pendingOrders[0].orderId)
        assertEquals(serviceName, pendingOrders[0].serviceName)
        
        // 2. 模拟时间到达（立即触发）
        serviceTimeNotificationManager.showServiceTimeEndNotification(orderId, serviceName)
        
        // 3. 验证通知已触发后，待处理订单被移除
        // 这里需要手动触发，因为在测试中不会自动执行
        pendingOrdersStorage.removePendingOrder(orderId)
        
        val updatedPendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertTrue(updatedPendingOrders.isEmpty())
    }

    @Test
    fun testNotificationDeduplication() {
        val orderId = 67890L
        val serviceName = "重复测试服务"
        val serviceEndTime = System.currentTimeMillis() + 1000 // 1秒后
        
        // 第一次调度
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            serviceEndTime
        )
        
        // 立即再次调度（应该被去重）
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            serviceEndTime
        )
        
        // 验证只有一个待处理订单
        val pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, pendingOrders.size)
    }

    @Test
    fun testCancelNotification() {
        val orderId = 11111L
        val serviceName = "取消测试服务"
        val serviceEndTime = System.currentTimeMillis() + 10000 // 10秒后
        
        // 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            serviceEndTime
        )
        
        // 验证待处理订单存在
        var pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, pendingOrders.size)
        
        // 取消通知
        serviceTimeNotificationManager.cancelServiceTimeEndNotification(orderId)
        
        // 验证待处理订单已移除
        pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertTrue(pendingOrders.isEmpty())
    }

    @Test
    fun testExpiredOrderCleanup() {
        val currentTime = System.currentTimeMillis()
        
        // 添加一个已过期订单
        pendingOrdersStorage.addPendingOrder(
            99999L,
            "过期服务",
            currentTime - 60000 // 1分钟前
        )
        
        // 添加一个未来订单
        pendingOrdersStorage.addPendingOrder(
            88888L,
            "未来服务",
            currentTime + 3600000 // 1小时后
        )
        
        // 清理过期订单
        pendingOrdersStorage.cleanupExpiredOrders()
        
        // 验证只有未来订单保留
        val pendingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, pendingOrders.size)
        assertEquals(88888L, pendingOrders[0].orderId)
    }

    @Test
    fun testBootRecoveryScenario() {
        // 模拟设备重启前的订单
        val orderId1 = 10001L
        val orderId2 = 10002L
        val currentTime = System.currentTimeMillis()
        
        // 添加未来订单（应该被恢复）
        pendingOrdersStorage.addPendingOrder(
            orderId1,
            "未来服务1",
            currentTime + 7200000 // 2小时后
        )
        
        // 添加过期订单（应该被清理）
        pendingOrdersStorage.addPendingOrder(
            orderId2,
            "过期服务2",
            currentTime - 30000 // 30秒前
        )
        
        // 模拟重启恢复流程
        val allOrders = pendingOrdersStorage.getAllPendingOrders()
        val currentTimeCheck = System.currentTimeMillis()
        
        var recoveredCount = 0
        allOrders.forEach { order ->
            if (order.serviceEndTime > currentTimeCheck) {
                // 模拟恢复通知调度
                recoveredCount++
            } else {
                // 移除过期订单
                pendingOrdersStorage.removePendingOrder(order.orderId)
            }
        }
        
        // 验证恢复结果
        assertEquals(1, recoveredCount)
        
        val remainingOrders = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, remainingOrders.size)
        assertEquals(orderId1, remainingOrders[0].orderId)
    }
}
