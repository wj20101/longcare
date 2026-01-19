package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
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
            
            // 追踪闹钟设置成功事件
            CountdownEventTracker.trackEvent(
                eventType = CountdownEventTracker.EventType.ALARM_SCHEDULE_SUCCESS,
                orderId = orderId,
                extras = mapOf(
                    "serviceName" to serviceName,
                    "triggerTime" to triggerTimeMillis,
                    "useAlarmClock" to shouldUseAlarmClock,
                    "nextAlarmTime" to nextAlarmClock?.triggerTime
                )
            )
            
        } catch (e: Exception) {
            logE("❌ 设置倒计时闹钟失败: ${e.message}", throwable = e)
            
            // 追踪闹钟设置失败事件
            CountdownEventTracker.trackError(
                eventType = CountdownEventTracker.EventType.ALARM_SCHEDULE_FAILED,
                orderId = orderId,
                throwable = e,
                extras = mapOf(
                    "serviceName" to serviceName,
                    "triggerTime" to triggerTimeMillis
                )
            )
        }
    }

    /**
     * 取消倒计时闹钟
     * 
     * 注意：由于设置闹钟时使用了动态action，取消时需要使用相同的方式创建PendingIntent
     * 或者使用FLAG_UPDATE_CURRENT来确保能找到对应的PendingIntent
     */
    fun cancelCountdownAlarm() {
        try {
            logI("开始取消倒计时闹钟...")
            
            // 方法1：使用FLAG_UPDATE_CURRENT创建PendingIntent来取消
            // 这种方式不需要知道原来的action，只要requestCode相同就能取消
            val intent = Intent(context, CountdownAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 取消AlarmManager中的闹钟
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            logI("✅ 倒计时闹钟已取消 (通过FLAG_UPDATE_CURRENT)")
            
            // 方法2：额外尝试取消可能存在的AlarmClock
            // AlarmClock设置的闹钟可能需要单独取消
            try {
                val nextAlarmClock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    alarmManager.nextAlarmClock
                } else {
                    null
                }
                if (nextAlarmClock != null) {
                    logI("当前系统下一个闹钟时间: ${nextAlarmClock.triggerTime}")
                }
            } catch (e: Exception) {
                // 忽略获取下一个闹钟信息的错误
            }
            
        } catch (e: Exception) {
            logE("❌ 取消倒计时闹钟失败: ${e.message}", throwable = e)
        }
    }
    
    /**
     * 取消指定订单的倒计时闹钟
     * 
     * @param orderId 订单ID，用于匹配设置闹钟时使用的action
     */
    fun cancelCountdownAlarmForOrder(orderId: Long) {
        try {
            logI("开始取消订单 $orderId 的倒计时闹钟...")
            
            // 使用与设置闹钟时相同的action来创建Intent
            val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
                action = "COUNTDOWN_ALARM_$orderId"
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                COUNTDOWN_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                logI("✅ 订单 $orderId 的倒计时闹钟已取消")
            } else {
                logI("⚠️ 没有找到订单 $orderId 的待取消闹钟，尝试通用取消...")
                // 如果找不到特定订单的闹钟，尝试通用取消
                cancelCountdownAlarm()
            }
        } catch (e: Exception) {
            logE("❌ 取消订单 $orderId 的倒计时闹钟失败: ${e.message}", throwable = e)
            // 失败时尝试通用取消
            cancelCountdownAlarm()
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
        logI("构建倒计时完成通知: orderId=$orderId, serviceName=$serviceName")
        
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
        // 注意：fullScreenIntent需要使用FLAG_ACTIVITY_NEW_TASK
        val alarmActivityIntent = CountdownAlarmActivity.createIntent(
            context,
            orderId.toString(),
            serviceName,
            autoCloseEnabled = true
        ).apply {
            // 确保Activity可以从后台启动
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val alarmActivityPendingIntent = PendingIntent.getActivity(
            context,
            COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 检查是否有全屏Intent权限（使用Compat API）
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val canUseFullScreenIntent = notificationManagerCompat.canUseFullScreenIntent()
        logI("全屏Intent权限: canUseFullScreenIntent=$canUseFullScreenIntent, SDK=${Build.VERSION.SDK_INT}")

        // 构建通知
        val builder = NotificationCompat.Builder(context, COUNTDOWN_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⏰ 服务倒计时完成")
            .setContentText("$serviceName 服务时间已到，请及时处理")
            .setSmallIcon(R.mipmap.app_logo_round)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .setLights(0xFF0000FF.toInt(), 1000, 500)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(alarmActivityPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "关闭响铃",
                dismissPendingIntent
            )
            .setTimeoutAfter(0)
        
        // 设置全屏Intent（关键：在锁屏时显示Activity）
        if (canUseFullScreenIntent) {
            builder.setFullScreenIntent(alarmActivityPendingIntent, true)
            logI("✅ 已设置fullScreenIntent")
        } else {
            logE("❌ 无法使用fullScreenIntent，需要用户授权")
            // Fallback: 增强 Heads-up 通知效果
            builder.setDefaults(NotificationCompat.DEFAULT_ALL) // 启用声音和震动
            builder.setVibrate(longArrayOf(0, 500, 200, 500, 200, 500)) // 自定义震动模式
            logI("⚠️ 使用 Fallback Heads-up 通知")
        }
        
        val notification = builder.build()
        logI("✅ 通知构建完成")
        return notification
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
     * 使用 NotificationChannelCompat 确保版本兼容性
     */
    private fun createNotificationChannel() {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        
        // 先删除旧的通知渠道（如果存在）
        try {
            notificationManagerCompat.deleteNotificationChannel("countdown_completion_channel")
        } catch (e: Exception) {
            // 忽略删除失败的错误
        }
        
        // 使用 NotificationChannelCompat.Builder 创建通知渠道
        val channel = NotificationChannelCompat.Builder(
            COUNTDOWN_NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("倒计时完成通知")
            .setDescription("服务倒计时完成时的提醒通知")
            .setVibrationEnabled(false) // 不使用通知震动，由AlarmRingtoneService处理
            .setLightsEnabled(true)
            .setShowBadge(true)
            .setSound(null, null) // 不设置声音，由AlarmRingtoneService处理
            .build()
        
        notificationManagerCompat.createNotificationChannel(channel)
        logI("倒计时通知渠道已重新创建: $COUNTDOWN_NOTIFICATION_CHANNEL_ID")
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
     * 检查是否有全屏Intent权限（Android 14+）
     * 在Android 14+上，应用需要用户授权才能使用全屏Intent在锁屏上显示Activity
     * 使用 NotificationManagerCompat 确保版本兼容性
     */
    fun canUseFullScreenIntent(): Boolean {
        val canUse = NotificationManagerCompat.from(context).canUseFullScreenIntent()
        logI("fullScreenIntent权限检查: canUse=$canUse, SDK=${Build.VERSION.SDK_INT}")
        return canUse
    }
    
    /**
     * 检查 fullScreenIntent 权限，返回详细状态
     * @return FullScreenIntentStatus 包含权限状态和API级别信息
     */
    fun getFullScreenIntentPermissionStatus(): FullScreenIntentStatus {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14 (API 34) 以下无需权限，自动授予
                FullScreenIntentStatus.GRANTED_BY_DEFAULT
            }
            NotificationManagerCompat.from(context).canUseFullScreenIntent() -> {
                FullScreenIntentStatus.GRANTED
            }
            else -> {
                FullScreenIntentStatus.DENIED
            }
        }
    }
    
    /**
     * fullScreenIntent 权限状态枚举
     */
    enum class FullScreenIntentStatus {
        /** Android 14以下，默认授予 */
        GRANTED_BY_DEFAULT,
        /** 用户已授权 */
        GRANTED,
        /** 用户拒绝或未授权 */
        DENIED
    }
}