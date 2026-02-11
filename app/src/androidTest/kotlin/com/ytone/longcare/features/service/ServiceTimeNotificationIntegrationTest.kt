package com.ytone.longcare.features.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 服务时间结束通知集成测试
 * 验证完整的通知流程
 */
@RunWith(AndroidJUnit4::class)
class ServiceTimeNotificationIntegrationTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var workManager: WorkManager
    private lateinit var pendingOrdersStorage: PendingOrdersStorage
    private lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 初始化WorkManager
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

        try {
            WorkManager.initialize(context, config)
        } catch (_: IllegalStateException) {
            // WorkManager 可能已在同一进程中初始化，复用现有实例即可。
        }
        workManager = WorkManager.getInstance(context)
        
        // 使用真实的服务管理器
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        pendingOrdersStorage = PendingOrdersStorage(context)
        pendingOrdersStorage.clearAllPendingOrders()
        serviceTimeNotificationManager = ServiceTimeNotificationManager(
            context,
            notificationManager,
            alarmManager,
            workManager,
            pendingOrdersStorage
        )
    }

    @After
    fun tearDown() {
        pendingOrdersStorage.clearAllPendingOrders()
    }

    /**
     * 测试完整的服务时间结束通知流程
     */
    @Test
    fun testCompleteServiceTimeEndNotificationFlow() {
        val orderId = 99999L
        val serviceName = "集成测试服务"
        val delaySeconds = 3L
        val endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds)

        // 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )

        val pending = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(1, pending.size)
        assertEquals(orderId, pending[0].orderId)
        assertEquals(serviceName, pending[0].serviceName)
    }

    /**
     * 测试通知取消功能
     */
    @Test
    fun testNotificationCancellation() {
        val orderId = 88888L
        val serviceName = "取消测试服务"
        val endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        
        // 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 立即取消
        serviceTimeNotificationManager.cancelServiceTimeEndNotification(orderId)

        val pending = pendingOrdersStorage.getAllPendingOrders()
        assertTrue(pending.isEmpty())
    }

    /**
     * 测试多重通知调度
     */
    @Test
    fun testMultipleNotificationScheduling() {
        val notifications = listOf(
            Triple(11111L, "服务A", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2)),
            Triple(22222L, "服务B", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(4)),
            Triple(33333L, "服务C", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(6))
        )
        
        // 调度多个通知
        notifications.forEach { (orderId, serviceName, endTime) ->
            serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
                orderId,
                serviceName,
                endTime
            )
        }

        val pending = pendingOrdersStorage.getAllPendingOrders()
        assertEquals(3, pending.size)
    }
}
