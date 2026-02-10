package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.*
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务时间结束通知管理器
 * 实现三重保障机制：AlarmManager + WorkManager + Handler
 * 确保服务时间到达时100%触发通知
 */
@Singleton
class ServiceTimeNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager,
    private val pendingOrdersStorage: PendingOrdersStorage
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerRunnables = ConcurrentHashMap<Long, Runnable>()

    companion object {
        private const val TAG = "ServiceTimeNotificationManager"
        
        // 通知渠道ID
        private const val SERVICE_TIME_CHANNEL_ID = "service_time_end_channel_v2"
        private const val SERVICE_TIME_NOTIFICATION_ID = 4001
        
        // AlarmManager请求码
        private const val ALARM_REQUEST_CODE = 5001
        
        // WorkManager相关
        private const val WORK_TAG = "service_time_end_work"
        private const val UNIQUE_WORK_NAME = "service_time_end_unique_work"
        
        // Intent extras
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        const val EXTRA_SERVICE_END_TIME = "extra_service_end_time"
        
        // 去重相关
        private const val PREFS_NAME = "service_time_notification_prefs"
        private const val KEY_LAST_PROCESSED_ORDER = "last_processed_order_"
        
        // 重试配置
        const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_SECONDS = 30L

        private const val INT_POSITIVE_MASK = 0x7fffffff
    }

    init {
        createNotificationChannel()
    }

    /**
     * 调度服务时间结束通知（三重保障）
     * @param orderId 订单ID
     * @param serviceName 服务名称
     * @param serviceEndTimeMillis 服务结束时间（毫秒时间戳）
     */
    fun scheduleServiceTimeEndNotification(
        orderId: Long,
        serviceName: String,
        serviceEndTimeMillis: Long
    ) {
        try {
            logI("调度服务时间结束通知: orderId=$orderId, serviceName=$serviceName, endTime=$serviceEndTimeMillis")
            
            // 检查任务是否已处理
            if (isNotificationAlreadyProcessed(orderId)) {
                logI("订单已处理，跳过重复通知: orderId=$orderId")
                return
            }
            
            val currentTime = System.currentTimeMillis()
            val delayMillis = serviceEndTimeMillis - currentTime
            
            // 如果服务时间已过，立即触发通知
            if (delayMillis <= 0) {
                logI("服务时间已过，立即触发通知: orderId=$orderId")
                showServiceTimeEndNotification(orderId, serviceName)
                return
            }
            
            // 保存待处理订单信息（用于设备重启恢复）
            pendingOrdersStorage.addPendingOrder(orderId, serviceName, serviceEndTimeMillis)
            
            // 第一重保障：AlarmManager（精确时间触发）
            scheduleAlarmManagerNotification(orderId, serviceName, serviceEndTimeMillis)
            
            // 第二重保障：WorkManager（应对设备休眠）
            scheduleWorkManagerNotification(orderId, serviceName, delayMillis)
            
            // 第三重保障：Handler（应用内兜底）
            scheduleHandlerNotification(orderId, serviceName, delayMillis)
            
            logI("三重保障通知调度完成: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("调度服务时间结束通知失败: ${e.message}")
            throw e
        }
    }

    /**
     * 取消服务时间结束通知
     */
    fun cancelServiceTimeEndNotification(orderId: Long) {
        try {
            logI("取消服务时间结束通知: orderId=$orderId")
            
            // 从存储中移除待处理订单
            pendingOrdersStorage.removePendingOrder(orderId)
            
            // 取消AlarmManager
            cancelAlarmManagerNotification(orderId)
            
            // 取消WorkManager
            cancelWorkManagerNotification(orderId)

            // 取消Handler
            cancelHandlerNotification(orderId)
            
            // 清除处理标记
            clearNotificationProcessedMark(orderId)
            
            logI("服务时间结束通知已取消: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("取消服务时间结束通知失败: ${e.message}")
        }
    }

    /**
     * 第一重保障：AlarmManager
     */
    private fun scheduleAlarmManagerNotification(
        orderId: Long,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        try {
            val intent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
                putExtra(EXTRA_SERVICE_END_TIME, triggerTimeMillis)
                action = "com.ytone.longcare.SERVICE_TIME_END_ALARM"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                buildAlarmRequestCode(orderId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Android 12+ 检查精确闹钟权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                logE("无精确闹钟权限，使用AlarmClock兜底")
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    triggerTimeMillis,
                    pendingIntent
                )
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                // 使用精确闹钟确保准时触发
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }

            logI("AlarmManager通知已设置: orderId=$orderId, triggerTime=$triggerTimeMillis")
            
        } catch (e: Exception) {
            logE("设置AlarmManager通知失败: ${e.message}")
            throw e
        }
    }

    /**
     * 第二重保障：WorkManager
     */
    private fun scheduleWorkManagerNotification(
        orderId: Long,
        serviceName: String,
        delayMillis: Long
    ) {
        try {
            val data = workDataOf(
                EXTRA_ORDER_ID to orderId,
                EXTRA_SERVICE_NAME to serviceName
            )

            val workRequest = OneTimeWorkRequestBuilder<ServiceTimeEndWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RETRY_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            // 使用唯一工作避免重复
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME + orderId,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            logI("WorkManager通知已设置: orderId=$orderId, delay=$delayMillis")
            
        } catch (e: Exception) {
            logE("设置WorkManager通知失败: ${e.message}")
            throw e
        }
    }

    /**
     * 第三重保障：Handler
     */
    private fun scheduleHandlerNotification(
        orderId: Long,
        serviceName: String,
        delayMillis: Long
    ) {
        try {
            cancelHandlerNotification(orderId)
            val runnable = Runnable {
                try {
                    if (!isNotificationAlreadyProcessed(orderId)) {
                        logI("Handler兜底通知触发: orderId=$orderId")
                        showServiceTimeEndNotification(orderId, serviceName)
                    }
                } catch (e: Exception) {
                    logE("Handler通知执行失败: ${e.message}")
                } finally {
                    handlerRunnables.remove(orderId)
                }
            }
            handlerRunnables[orderId] = runnable
            mainHandler.postDelayed(runnable, delayMillis)

            logI("Handler通知已设置: orderId=$orderId, delay=$delayMillis")
            
        } catch (e: Exception) {
            logE("设置Handler通知失败: ${e.message}")
            throw e
        }
    }

    /**
     * 显示服务时间结束通知
     */
    fun showServiceTimeEndNotification(orderId: Long, serviceName: String) {
        try {
            if (isNotificationAlreadyProcessed(orderId)) {
                logI("通知已处理，跳过显示: orderId=$orderId")
                return
            }
            logI("显示服务时间结束通知: orderId=$orderId, serviceName=$serviceName")
            
            val notification = NotificationCompat.Builder(context, SERVICE_TIME_CHANNEL_ID)
                .setContentTitle("服务时间提醒")
                .setContentText("$serviceName 服务时间即将结束，请及时处理")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            notificationManager.notify(buildNotificationId(orderId), notification)
            markNotificationAsProcessed(orderId)
            cancelHandlerNotification(orderId)
            
            logI("服务时间结束通知已显示: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("显示服务时间结束通知失败: ${e.message}")
            throw e
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_TIME_CHANNEL_ID,
                "服务时间结束提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "服务时间结束时的重要提醒通知"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 取消AlarmManager通知
     */
    private fun cancelAlarmManagerNotification(orderId: Long) {
        try {
            val intent = Intent(context, ServiceTimeAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                buildAlarmRequestCode(orderId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            logI("AlarmManager通知已取消: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("取消AlarmManager通知失败: ${e.message}")
        }
    }

    /**
     * 取消WorkManager通知
     */
    private fun cancelWorkManagerNotification(orderId: Long) {
        try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME + orderId)
            logI("WorkManager通知已取消: orderId=$orderId")
            
        } catch (e: Exception) {
            logE("取消WorkManager通知失败: ${e.message}")
        }
    }

    /**
     * 取消Handler通知
     */
    private fun cancelHandlerNotification(orderId: Long) {
        val runnable = handlerRunnables.remove(orderId) ?: return
        mainHandler.removeCallbacks(runnable)
        logI("Handler通知已取消: orderId=$orderId")
    }

    /**
     * 检查是否已处理过该通知
     */
    private fun isNotificationAlreadyProcessed(orderId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastProcessed = prefs.getLong(KEY_LAST_PROCESSED_ORDER + orderId, 0)
        val currentTime = System.currentTimeMillis()
        
        // 30分钟内认为已处理（避免重复）
        return currentTime - lastProcessed < 30 * 60 * 1000
    }

    /**
     * 标记通知为已处理
     */
    private fun markNotificationAsProcessed(orderId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(KEY_LAST_PROCESSED_ORDER + orderId, System.currentTimeMillis())
        }
    }

    /**
     * 清除通知处理标记
     */
    private fun clearNotificationProcessedMark(orderId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_LAST_PROCESSED_ORDER + orderId) }
    }

    /**
     * 检查是否有精确闹钟权限（Android 12+）
     */
    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun buildAlarmRequestCode(orderId: Long): Int {
        val hash = ((orderId xor (orderId ushr 32)).toInt() and INT_POSITIVE_MASK)
        val range = Int.MAX_VALUE - ALARM_REQUEST_CODE
        return ALARM_REQUEST_CODE + (hash % range)
    }

    private fun buildNotificationId(orderId: Long): Int {
        val hash = ((orderId xor (orderId ushr 32)).toInt() and INT_POSITIVE_MASK)
        val range = Int.MAX_VALUE - SERVICE_TIME_NOTIFICATION_ID
        return SERVICE_TIME_NOTIFICATION_ID + (hash % range)
    }
}
