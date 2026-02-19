package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import java.util.concurrent.TimeUnit

internal class ServiceTimeNotificationScheduleDelegate(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager,
    private val alarmRequestCodeSeed: Int,
    private val uniqueWorkNamePrefix: String,
    private val workTag: String,
    private val retryDelaySeconds: Long,
) {
    private val fallbackScheduler = ServiceTimeFallbackScheduler()

    fun scheduleAlarmManagerNotification(
        orderId: Long,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        try {
            val intent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                putExtra(ServiceTimeNotificationManager.EXTRA_ORDER_ID, orderId)
                putExtra(ServiceTimeNotificationManager.EXTRA_SERVICE_NAME, serviceName)
                putExtra(ServiceTimeNotificationManager.EXTRA_SERVICE_END_TIME, triggerTimeMillis)
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
            }
            val pendingIntent = PendingIntentCompat.getBroadcast(
                context,
                buildAlarmRequestCode(orderId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false
            ) ?: throw IllegalStateException("创建 AlarmManager PendingIntent 失败: orderId=$orderId")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                logE("无精确闹钟权限，降级为 setAndAllowWhileIdle")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
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

    fun scheduleWorkManagerNotification(
        orderId: Long,
        serviceName: String,
        delayMillis: Long
    ) {
        try {
            val data = workDataOf(
                ServiceTimeNotificationManager.EXTRA_ORDER_ID to orderId,
                ServiceTimeNotificationManager.EXTRA_SERVICE_NAME to serviceName
            )
            val workRequest = OneTimeWorkRequestBuilder<ServiceTimeEndWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(workTag)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    retryDelaySeconds,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkNamePrefix + orderId,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            logI("WorkManager通知已设置: orderId=$orderId, delay=$delayMillis")
        } catch (e: Exception) {
            logE("设置WorkManager通知失败: ${e.message}")
            throw e
        }
    }

    fun scheduleFallbackNotification(
        orderId: Long,
        delayMillis: Long,
        onTrigger: () -> Unit
    ) {
        fallbackScheduler.scheduleFallbackNotification(orderId, delayMillis, onTrigger)
    }

    fun cancelAlarmManagerNotification(orderId: Long) {
        try {
            val intent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
            }
            val pendingIntent = PendingIntentCompat.getBroadcast(
                context,
                buildAlarmRequestCode(orderId),
                intent,
                PendingIntent.FLAG_NO_CREATE,
                false
            )
            if (pendingIntent == null) {
                logI("AlarmManager通知不存在，跳过取消: orderId=$orderId")
                return
            }
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            logI("AlarmManager通知已取消: orderId=$orderId")
        } catch (e: Exception) {
            logE("取消AlarmManager通知失败: ${e.message}")
        }
    }

    fun cancelWorkManagerNotification(orderId: Long) {
        try {
            workManager.cancelUniqueWork(uniqueWorkNamePrefix + orderId)
            logI("WorkManager通知已取消: orderId=$orderId")
        } catch (e: Exception) {
            logE("取消WorkManager通知失败: ${e.message}")
        }
    }

    fun cancelFallbackNotification(orderId: Long) {
        fallbackScheduler.cancelFallbackNotification(orderId)
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun buildAlarmRequestCode(orderId: Long): Int {
        val positiveHash = ((orderId xor (orderId ushr 32)).toInt() and INT_POSITIVE_MASK)
        val range = Int.MAX_VALUE - alarmRequestCodeSeed
        return alarmRequestCodeSeed + (positiveHash % range)
    }

    private companion object {
        private const val INT_POSITIVE_MASK = 0x7fffffff
    }
}
