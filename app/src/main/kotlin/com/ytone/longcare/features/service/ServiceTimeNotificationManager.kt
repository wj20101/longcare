package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务时间结束通知管理器
 * 实现三重保障机制：AlarmManager + WorkManager + Coroutine fallback
 */
@Singleton
class ServiceTimeNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager,
    private val workManager: androidx.work.WorkManager,
    private val pendingOrdersStorage: PendingOrdersStorage
) {
    companion object {
        private const val SERVICE_TIME_CHANNEL_ID = "service_time_end_channel_v2"
        private const val SERVICE_TIME_NOTIFICATION_ID = 4001
        private const val ALARM_REQUEST_CODE = 5001

        private const val WORK_TAG = "service_time_end_work"
        private const val UNIQUE_WORK_NAME = "service_time_end_unique_work"

        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        const val EXTRA_SERVICE_END_TIME = "extra_service_end_time"
        const val ACTION_SERVICE_TIME_END_ALARM = "com.ytone.longcare.SERVICE_TIME_END_ALARM"

        private const val PREFS_NAME = "service_time_notification_prefs"
        private const val KEY_LAST_PROCESSED_ORDER = "last_processed_order_"
        private const val DEDUPLICATE_WINDOW_MILLIS = 30 * 60 * 1000L

        const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_SECONDS = 30L
    }

    private val recordDelegate by lazy {
        ServiceTimeNotificationRecordDelegate(
            context = context,
            prefsName = PREFS_NAME,
            keyLastProcessedPrefix = KEY_LAST_PROCESSED_ORDER,
            deduplicateWindowMillis = DEDUPLICATE_WINDOW_MILLIS,
        )
    }

    private val scheduleDelegate by lazy {
        ServiceTimeNotificationScheduleDelegate(
            context = context,
            alarmManager = alarmManager,
            workManager = workManager,
            alarmRequestCodeSeed = ALARM_REQUEST_CODE,
            uniqueWorkNamePrefix = UNIQUE_WORK_NAME,
            workTag = WORK_TAG,
            retryDelaySeconds = RETRY_DELAY_SECONDS,
        )
    }

    private val notificationUiDelegate by lazy {
        ServiceTimeNotificationUiDelegate(
            context = context,
            notificationManager = notificationManager,
            channelId = SERVICE_TIME_CHANNEL_ID,
            notificationIdSeed = SERVICE_TIME_NOTIFICATION_ID,
        )
    }

    init {
        notificationUiDelegate.createNotificationChannel()
    }

    fun scheduleServiceTimeEndNotification(
        orderId: Long,
        serviceName: String,
        serviceEndTimeMillis: Long
    ) {
        try {
            logI("调度服务时间结束通知: orderId=$orderId, serviceName=$serviceName, endTime=$serviceEndTimeMillis")
            if (recordDelegate.isNotificationAlreadyProcessed(orderId)) {
                logI("订单已处理，跳过重复通知: orderId=$orderId")
                return
            }

            val delayMillis = serviceEndTimeMillis - System.currentTimeMillis()
            if (delayMillis <= 0) {
                logI("服务时间已过，立即触发通知: orderId=$orderId")
                showServiceTimeEndNotification(orderId, serviceName)
                return
            }

            pendingOrdersStorage.addPendingOrder(orderId, serviceName, serviceEndTimeMillis)
            scheduleDelegate.scheduleAlarmManagerNotification(orderId, serviceName, serviceEndTimeMillis)
            scheduleDelegate.scheduleWorkManagerNotification(orderId, serviceName, delayMillis)
            scheduleDelegate.scheduleFallbackNotification(orderId, delayMillis) {
                if (!recordDelegate.isNotificationAlreadyProcessed(orderId)) {
                    logI("Coroutine兜底通知触发: orderId=$orderId")
                    showServiceTimeEndNotification(orderId, serviceName)
                }
            }
            logI("三重保障通知调度完成: orderId=$orderId")
        } catch (e: Exception) {
            logE("调度服务时间结束通知失败: ${e.message}")
            throw e
        }
    }

    fun cancelServiceTimeEndNotification(orderId: Long) {
        try {
            logI("取消服务时间结束通知: orderId=$orderId")
            pendingOrdersStorage.removePendingOrder(orderId)
            scheduleDelegate.cancelAlarmManagerNotification(orderId)
            scheduleDelegate.cancelWorkManagerNotification(orderId)
            scheduleDelegate.cancelFallbackNotification(orderId)
            recordDelegate.clearNotificationProcessedMark(orderId)
            logI("服务时间结束通知已取消: orderId=$orderId")
        } catch (e: Exception) {
            logE("取消服务时间结束通知失败: ${e.message}")
        }
    }

    fun showServiceTimeEndNotification(orderId: Long, serviceName: String) {
        try {
            if (recordDelegate.isNotificationAlreadyProcessed(orderId)) {
                logI("通知已处理，跳过显示: orderId=$orderId")
                return
            }
            logI("显示服务时间结束通知: orderId=$orderId, serviceName=$serviceName")
            notificationUiDelegate.showServiceTimeEndNotification(orderId, serviceName)
            recordDelegate.markNotificationAsProcessed(orderId)
            scheduleDelegate.cancelFallbackNotification(orderId)
            logI("服务时间结束通知已显示: orderId=$orderId")
        } catch (e: Exception) {
            logE("显示服务时间结束通知失败: ${e.message}")
            throw e
        }
    }
}
