package com.ytone.longcare.features.countdown.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import com.ytone.longcare.model.OrderKey

internal class CountdownNotificationUiDelegate(
    private val context: Context,
    private val channelId: String,
    private val dismissAlarmRequestCode: Int,
    private val countdownAlarmActivityRequestCode: Int,
    private val fallbackChannelId: String = "${channelId}_fallback",
) {
    fun buildCountdownCompletionNotification(
        orderKey: OrderKey,
        serviceName: String,
        owner: String,
        triggerTimeMillis: Long,
    ): android.app.Notification = buildNotification(orderKey, serviceName, owner, triggerTimeMillis, fallback = false)

    fun buildCountdownFallbackNotification(
        orderKey: OrderKey,
        serviceName: String,
        owner: String,
        triggerTimeMillis: Long,
    ): android.app.Notification = buildNotification(orderKey, serviceName, owner, triggerTimeMillis, fallback = true)

    private fun buildNotification(
        orderKey: OrderKey,
        serviceName: String,
        owner: String,
        triggerTimeMillis: Long,
        fallback: Boolean,
    ): android.app.Notification {
        logI("构建倒计时完成通知: orderId=${orderKey.orderId}, serviceName=$serviceName")

        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
            putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, owner)
            putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, triggerTimeMillis)
            action = "com.ytone.longcare.COUNTDOWN_DISMISS_${orderKey.orderId}_${orderKey.planId}_$triggerTimeMillis"
        }
        val dismissPendingIntent = PendingIntentCompat.getBroadcast(
            context,
            dismissAlarmRequestCode,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val alarmActivityIntent = CountdownAlarmActivity.createIntent(
            context,
            orderKey,
            serviceName,
            owner = owner,
            triggerTimeMillis = triggerTimeMillis,
        )
        val alarmActivityPendingIntent = PendingIntentCompat.getActivity(
            context,
            countdownAlarmActivityRequestCode,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val builder = NotificationCompat.Builder(context, if (fallback) fallbackChannelId else channelId)
            .setContentTitle(context.getString(R.string.countdown_notification_title))
            .setContentText(
                context.getString(R.string.countdown_notification_content, serviceName),
            )
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
            .setTimeoutAfter(0)

        if (fallback) {
            // On API 26+ the fallback channel owns sound and the user can mute/change it.
            // Pre-channel devices use the same selected default alarm, not a replacement tone.
            @Suppress("DEPRECATION")
            builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, AudioManager.STREAM_ALARM)
        }

        if (alarmActivityPendingIntent != null) {
            builder.setContentIntent(alarmActivityPendingIntent)
        } else {
            logE("⚠️ 倒计时通知 contentIntent 为空，点击通知不会跳转")
        }

        if (dismissPendingIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.countdown_notification_stop_ringing),
                dismissPendingIntent
            )
        } else {
            logE("⚠️ 倒计时通知关闭动作 PendingIntent 为空，忽略关闭按钮")
        }

        // 始终提交 FSI 请求；Android 14+ 在权限被拒绝时会移除 FSI，转为受限的
        // heads-up 提醒。提前省略请求会跳过系统的这条降级链路。
        if (alarmActivityPendingIntent != null) {
            builder.setFullScreenIntent(alarmActivityPendingIntent, true)
            logI("已提交 fullScreenIntent 请求，由系统根据权限与屏幕状态决定展示方式")
        } else {
            logE("❌ Activity PendingIntent 为空，无法提交 fullScreenIntent 请求")
        }

        val notification = builder.build()
        logI("✅ 通知构建完成")
        return notification
    }

    fun createNotificationChannel() {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        try {
            notificationManagerCompat.deleteNotificationChannel("countdown_completion_channel")
        } catch (exception: Exception) {
            logE("删除旧倒计时通知渠道失败: ${exception.message}", throwable = exception)
        }

        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName(context.getString(R.string.countdown_notification_channel_name))
            .setDescription(
                context.getString(R.string.countdown_notification_channel_description),
            )
            .setVibrationEnabled(false)
            .setLightsEnabled(true)
            .setShowBadge(true)
            .setSound(null, null)
            .build()

        notificationManagerCompat.createNotificationChannel(channel)
        val fallbackChannel = NotificationChannelCompat.Builder(
            fallbackChannelId, NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.countdown_fallback_notification_channel_name))
            .setDescription(context.getString(R.string.countdown_fallback_notification_channel_description))
            .setVibrationEnabled(false)
            .setShowBadge(true)
            .setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build()
        // Recreating an existing channel must not override the user's sound/importance choices.
        notificationManagerCompat.createNotificationChannel(fallbackChannel)
        logI("倒计时通知渠道已确保存在: $channelId")
    }

    fun canUseFullScreenIntent(): Boolean {
        val canUse = NotificationManagerCompat.from(context).canUseFullScreenIntent()
        logI("fullScreenIntent权限检查: canUse=$canUse, SDK=${Build.VERSION.SDK_INT}")
        return canUse
    }
}
