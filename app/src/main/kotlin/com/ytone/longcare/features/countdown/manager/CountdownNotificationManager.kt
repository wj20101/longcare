package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 倒计时通知管理器
 * 负责管理倒计时完成时的通知和AlarmManager
 */
@Singleton
class CountdownNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager
) {

    companion object {
        private const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel"
        private const val COUNTDOWN_NOTIFICATION_ID = 2001
        private const val COUNTDOWN_ALARM_REQUEST_CODE = 3001
        
        // Intent extras
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
    }

    init {
        createNotificationChannel()
    }

    /**
     * 设置倒计时完成闹钟
     * @param orderId 订单ID
     * @param serviceName 服务名称
     * @param triggerTimeMillis 触发时间（毫秒时间戳）
     */
    fun scheduleCountdownAlarm(
        orderId: Long,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        try {
            val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用精确闹钟确保准时触发
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )

            logI("倒计时闹钟已设置: orderId=$orderId, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
        } catch (e: Exception) {
            logE("设置倒计时闹钟失败: ${e.message}")
        }
    }

    /**
     * 取消倒计时闹钟
     */
    fun cancelCountdownAlarm() {
        try {
            val intent = Intent(context, CountdownAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            logI("倒计时闹钟已取消")
        } catch (e: Exception) {
            logE("取消倒计时闹钟失败: ${e.message}")
        }
    }

    /**
     * 显示倒计时完成通知
     * @param orderId 订单ID
     * @param serviceName 服务名称
     */
    fun showCountdownCompletionNotification(
        orderId: Long,
        serviceName: String
    ) {
        try {
            val notification = NotificationCompat.Builder(context, COUNTDOWN_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("服务倒计时完成")
                .setContentText("$serviceName 服务时间已到，请及时处理")
                .setSmallIcon(R.mipmap.app_logo_round)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // 声音、振动、LED
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
                .build()

            notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
            logI("倒计时完成通知已显示: orderId=$orderId, serviceName=$serviceName")
        } catch (e: Exception) {
            logE("显示倒计时完成通知失败: ${e.message}")
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COUNTDOWN_NOTIFICATION_CHANNEL_ID,
                "倒计时完成通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "服务倒计时完成时的提醒通知"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
            logI("倒计时通知渠道已创建")
        }
    }

    /**
     * 检查是否有精确闹钟权限（Android 12+）
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 计算倒计时完成的绝对时间
     * @param countdownDurationMillis 倒计时持续时间（毫秒）
     * @return 完成时间的时间戳
     */
    fun calculateCompletionTime(countdownDurationMillis: Long): Long {
        return System.currentTimeMillis() + countdownDurationMillis
    }
}