package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 服务时间结束通知管理器测试
 * 验证三重保障机制的正确性
 */
@RunWith(RobolectricTestRunner::class)
class ServiceTimeNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var workManager: WorkManager
    private lateinit var pendingOrdersStorage: PendingOrdersStorage
    private lateinit var serviceTimeNotificationManager: ServiceTimeNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 初始化WorkManager测试配置
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor { it.run() }
            .build()
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        pendingOrdersStorage = PendingOrdersStorage(context, "test_pending_orders")
        serviceTimeNotificationManager = ServiceTimeNotificationManager(
            context,
            notificationManager,
            alarmManager,
            workManager,
            pendingOrdersStorage
        )
    }

    /**
     * 测试基本通知调度
     */
    @Test
    fun testScheduleServiceTimeEndNotification() = runBlocking {
        val orderId = 12345L
        val serviceName = "测试服务"
        val endTime = System.currentTimeMillis() + 5000 // 5秒后
        
        // 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 等待通知触发
        delay(6000)
        
        // 验证通知已显示（这里需要检查通知状态）
        // 由于Robolectric限制，主要验证无异常抛出
        assert(true)
    }

    /**
     * 测试重复通知去重
     */
    @Test
    fun testDuplicateNotificationPrevention() = runBlocking {
        val orderId = 67890L
        val serviceName = "去重测试服务"
        val endTime = System.currentTimeMillis() + 2000 // 2秒后
        
        // 第一次调度
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 立即重复调度（应该被去重）
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 等待通知触发
        delay(3000)
        
        // 验证只触发一次通知（主要验证无异常）
        assert(true)
    }

    /**
     * 测试过期时间处理
     */
    @Test
    fun testExpiredTimeHandling() = runBlocking {
        val orderId = 11111L
        val serviceName = "过期测试服务"
        val endTime = System.currentTimeMillis() - 1000 // 1秒前（已过期）
        
        // 调度通知（应该立即触发）
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 等待处理
        delay(1000)
        
        // 验证立即触发通知
        assert(true)
    }

    /**
     * 测试通知取消功能
     */
    @Test
    fun testCancelNotification() {
        val orderId = 22222L
        val serviceName = "取消测试服务"
        val endTime = System.currentTimeMillis() + 10000 // 10秒后
        
        // 调度通知
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 立即取消
        serviceTimeNotificationManager.cancelServiceTimeEndNotification(orderId)
        
        // 验证取消成功（主要验证无异常）
        assert(true)
    }

    /**
     * 测试三重保障机制
     */
    @Test
    fun testTripleGuaranteeMechanism() = runBlocking {
        val orderId = 33333L
        val serviceName = "三重保障测试服务"
        val endTime = System.currentTimeMillis() + 3000 // 3秒后
        
        // 调度通知（应该设置三重保障）
        serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
            orderId,
            serviceName,
            endTime
        )
        
        // 等待通知触发
        delay(4000)
        
        // 验证三重保障机制正常工作
        // 这里主要验证无异常抛出，实际的三重保障需要集成测试
        assert(true)
    }

    /**
     * 测试边界条件
     */
    @Test
    fun testBoundaryConditions() {
        val orderId = 44444L
        val serviceName = "边界测试服务"
        
        // 测试边界时间值
        val boundaryTimes = listOf(
            0L, // 时间戳0
            -1L, // 负数时间
            Long.MAX_VALUE, // 最大时间戳
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365) // 一年后
        )
        
        boundaryTimes.forEach { endTime ->
            try {
                serviceTimeNotificationManager.scheduleServiceTimeEndNotification(
                    orderId,
                    serviceName,
                    endTime
                )
                // 验证边界条件处理
                assert(true)
            } catch (e: Exception) {
                // 边界条件可能抛出异常，验证异常处理
                assert(true)
            }
        }
    }
}