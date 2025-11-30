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
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
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
        private const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel_v2"
        private const val COUNTDOWN_NOTIFICATION_ID = 2001
        private const val COUNTDOWN_ALARM_REQUEST_CODE = 3001
        private const val DISMISS_ALARM_REQUEST_CODE = 3002
        private const val COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE = 3003
        
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
            logI("开始设置倒计时闹钟: orderId=$orderId, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
            
            // 先取消已存在的闹钟，避免重复
            cancelCountdownAlarm()
            
            val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
                // 添加唯一标识，确保Intent不会被复用
                action = "COUNTDOWN_ALARM_$orderId"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Activity PendingIntent 用于 AlarmClock 的显示入口和全屏
            val alarmActivityIntent = CountdownAlarmActivity.createIntent(
                context,
                orderId.toString(),
                serviceName,
                autoCloseEnabled = false // 禁用自动关闭，确保用户看到提醒
            )
            val alarmActivityPendingIntent = PendingIntent.getActivity(
                context,
                COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 优先使用 AlarmClock 确保锁屏提醒（Android 12+）
            // AlarmClock 可以绕过Doze模式和电池优化，确保在锁屏状态下也能触发
            val shouldUseAlarmClock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            
            if (shouldUseAlarmClock) {
                // 使用 AlarmClock 确保锁屏提醒
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    triggerTimeMillis,
                    alarmActivityPendingIntent // 直接使用Activity PendingIntent作为ShowIntent，点击系统闹钟图标时跳转
                )
                // AlarmManagerCompat 目前没有 setAlarmClock 方法，直接使用 alarmManager
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                logI("✅ 通过AlarmClock设置倒计时闹钟(确保锁屏提醒): orderId=$orderId, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
            } else {
                // Android 12以下使用精确闹钟
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                logI("✅ 通过ExactAndAllowWhileIdle设置倒计时闹钟: orderId=$orderId, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
            }

            // 验证闹钟是否设置成功
            val nextAlarmClock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.nextAlarmClock
            } else {
                null
            }
            logI("下一个闹钟时间: ${nextAlarmClock?.triggerTime}")
            
        } catch (e: Exception) {
            logE("❌ 设置倒计时闹钟失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 取消倒计时闹钟
     */
    fun cancelCountdownAlarm() {
        try {
            // 取消所有可能的闹钟（包括不同action的）
            val intent = Intent(context, CountdownAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                logI("✅ 倒计时闹钟已取消")
            } else {
                logI("⚠️ 没有找到待取消的倒计时闹钟")
            }
        } catch (e: Exception) {
            logE("❌ 取消倒计时闹钟失败: ${e.message}", throwable = e)
        }
    }

    /**
     * 构建倒计时完成通知
     * @param orderId 订单ID
     * @param serviceName 服务名称
     * @return Notification对象
     */
    fun buildCountdownCompletionNotification(
        orderId: Long,
        serviceName: String
    ): android.app.Notification {
        // 创建关闭响铃的PendingIntent
        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ORDER_ID, orderId)
            putExtra(EXTRA_SERVICE_NAME, serviceName)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            DISMISS_ALARM_REQUEST_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建启动CountdownAlarmActivity的PendingIntent
        val alarmActivityIntent = CountdownAlarmActivity.createIntent(
            context,
            orderId.toString(),
            serviceName,
            autoCloseEnabled = true
        )
        val alarmActivityPendingIntent = PendingIntent.getActivity(
            context,
            COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知仅用于显示信息和提供操作按钮，声音和震动由AlarmRingtoneService处理
        return NotificationCompat.Builder(context, COUNTDOWN_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⏰ 服务倒计时完成")
            .setContentText("$serviceName 服务时间已到，请及时处理")
            .setSmallIcon(R.mipmap.app_logo_round)
            .setPriority(NotificationCompat.PRIORITY_MAX) // 使用最高优先级
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false) // 不自动取消，需要用户手动关闭
            .setOngoing(true) // 设置为持续通知，不能滑动删除
            .setDefaults(0) // 清除默认设置，不使用默认声音和震动
            .setSound(null) // 不播放声音（由AlarmRingtoneService处理）
            .setVibrate(null) // 不震动（由AlarmRingtoneService处理）
            .setLights(0xFF0000FF.toInt(), 1000, 500) // 设置LED灯光
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏可见
            .setContentIntent(alarmActivityPendingIntent) // 点击通知时启动闹铃页面
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "关闭响铃",
                dismissPendingIntent
            )
            .setFullScreenIntent(alarmActivityPendingIntent, true) // 全屏显示闹铃页面
            .setTimeoutAfter(0) // 不自动超时
            .build()
    }

    /**
     * 显示倒计时完成通知 (已废弃，请使用 buildCountdownCompletionNotification 配合前台服务)
     * @param orderId 订单ID
     * @param serviceName 服务名称
     */
    @Deprecated("请在前台服务中使用 buildCountdownCompletionNotification")
    fun showCountdownCompletionNotification(
        orderId: Long,
        serviceName: String
    ) {
        try {
            val notification = buildCountdownCompletionNotification(orderId, serviceName)
            notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
            logI("倒计时完成通知已显示: orderId=$orderId, serviceName=$serviceName")
        } catch (e: Exception) {
            logE("显示倒计时完成通知失败: ${e.message}")
        }
    }

    /**
     * 取消倒计时完成通知
     */
    fun cancelCountdownCompletionNotification() {
        try {
            notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
            logI("倒计时完成通知已取消")
        } catch (e: Exception) {
            logE("取消倒计时完成通知失败: ${e.message}")
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 先删除旧的通知渠道（如果存在）
            try {
                notificationManager.deleteNotificationChannel("countdown_completion_channel")
            } catch (e: Exception) {
                // 忽略删除失败的错误
            }
            
            val channel = NotificationChannel(
                COUNTDOWN_NOTIFICATION_CHANNEL_ID,
                "倒计时完成通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "服务倒计时完成时的提醒通知"
                enableVibration(false) // 不使用通知震动，由AlarmRingtoneService处理
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                
                // 不设置声音，由AlarmRingtoneService处理
                setSound(null, null)
                
                // 允许绕过勿扰模式
                setBypassDnd(true)
                
                // 设置为可以在锁屏上显示
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            
            notificationManager.createNotificationChannel(channel)
            logI("倒计时通知渠道已重新创建: $COUNTDOWN_NOTIFICATION_CHANNEL_ID")
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