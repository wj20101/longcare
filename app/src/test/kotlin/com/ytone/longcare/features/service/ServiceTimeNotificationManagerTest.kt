package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceTimeNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var workManager: WorkManager
    private lateinit var pendingOrdersStorage: PendingOrdersStorage
    private lateinit var manager: ServiceTimeNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        pendingOrdersStorage = mockk(relaxed = true)

        manager = ServiceTimeNotificationManager(
            context = context,
            notificationManager = notificationManager,
            alarmManager = alarmManager,
            workManager = workManager,
            pendingOrdersStorage = pendingOrdersStorage
        )
    }

    @Test
    fun scheduleFuture_shouldPersistOrder_andScheduleWork() {
        val orderId = 12345L
        val serviceName = "测试服务"
        val endTime = System.currentTimeMillis() + 60_000L

        manager.scheduleServiceTimeEndNotification(orderId, serviceName, endTime)

        verify(exactly = 1) {
            pendingOrdersStorage.addPendingOrder(orderId, serviceName, endTime)
        }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "service_time_end_unique_work$orderId",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
        verify(exactly = 0) { notificationManager.notify(any(), any<Notification>()) }
    }

    @Test
    fun schedulePast_shouldNotifyImmediately_withoutPersistingOrScheduling() {
        val orderId = 23456L
        val serviceName = "过期服务"
        val endTime = System.currentTimeMillis() - 1_000L

        manager.scheduleServiceTimeEndNotification(orderId, serviceName, endTime)

        verify(exactly = 1) { notificationManager.notify(any(), any<Notification>()) }
        verify(exactly = 0) { pendingOrdersStorage.addPendingOrder(any(), any(), any()) }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun showNotification_shouldBeDeduplicatedWithinWindow() {
        val orderId = 34567L
        val serviceName = "去重服务"

        manager.showServiceTimeEndNotification(orderId, serviceName)
        manager.showServiceTimeEndNotification(orderId, serviceName)

        verify(exactly = 1) { notificationManager.notify(any(), any<Notification>()) }
    }

    @Test
    fun cancel_shouldRemovePending_andCancelUniqueWork() {
        val orderId = 45678L

        manager.cancelServiceTimeEndNotification(orderId)

        verify(exactly = 1) { pendingOrdersStorage.removePendingOrder(orderId) }
        verify(exactly = 1) { workManager.cancelUniqueWork("service_time_end_unique_work$orderId") }
    }

    @Test
    fun scheduleProcessedOrder_shouldSkipScheduling() {
        val orderId = 56789L
        val serviceName = "已处理服务"
        val endTime = System.currentTimeMillis() + 60_000L

        manager.showServiceTimeEndNotification(orderId, serviceName)
        clearMocks(notificationManager, pendingOrdersStorage, workManager)

        manager.scheduleServiceTimeEndNotification(orderId, serviceName, endTime)

        verify(exactly = 0) { pendingOrdersStorage.addPendingOrder(any(), any(), any()) }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
        verify(exactly = 0) { notificationManager.notify(any(), any<Notification>()) }
    }
}
