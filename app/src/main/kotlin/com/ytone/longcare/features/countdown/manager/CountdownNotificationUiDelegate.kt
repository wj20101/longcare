package com.ytone.longcare.features.countdown.manager

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

internal enum class CountdownAlarmLaunchSource {
    FULL_SCREEN_NOTIFICATION,
    DIRECT_SERVICE_LAUNCH,
}

internal object CountdownAlarmPresentationPolicy {
    fun autoCloseEnabled(launchSource: CountdownAlarmLaunchSource): Boolean {
        return when (launchSource) {
            CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION -> false
            CountdownAlarmLaunchSource.DIRECT_SERVICE_LAUNCH -> false
        }
    }
}

internal class CountdownNotificationUiDelegate(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val channelId: String,
    private val notificationId: Int,
    private val dismissAlarmRequestCode: Int,
    private val countdownAlarmActivityRequestCode: Int,
) {
    fun buildCountdownCompletionNotification(
        orderKey: OrderKey,
        serviceName: String
    ): android.app.Notification {
        logI("构建倒计时完成通知: orderId=${orderKey.orderId}, serviceName=$serviceName")

        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
            putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
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
            autoCloseEnabled = CountdownAlarmPresentationPolicy.autoCloseEnabled(
                launchSource = CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION
            )
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val alarmActivityPendingIntent = PendingIntentCompat.getActivity(
            context,
            countdownAlarmActivityRequestCode,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val canUseFullScreenIntent = notificationManagerCompat.canUseFullScreenIntent()
        logI("全屏Intent权限: canUseFullScreenIntent=$canUseFullScreenIntent, SDK=${Build.VERSION.SDK_INT}")

        val builder = NotificationCompat.Builder(context, channelId)
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

        if (canUseFullScreenIntent && alarmActivityPendingIntent != null) {
            builder.setFullScreenIntent(alarmActivityPendingIntent, true)
            logI("✅ 已设置fullScreenIntent")
        } else if (canUseFullScreenIntent) {
            logE("❌ 可用 fullScreenIntent 但 Activity PendingIntent 为空，跳过设置")
        } else {
            logE("❌ 无法使用fullScreenIntent，需要用户授权")
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            builder.setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            logI("⚠️ 使用 Fallback Heads-up 通知")
        }

        val notification = builder.build()
        logI("✅ 通知构建完成")
        return notification
    }

    fun cancelCountdownCompletionNotification() {
        try {
            notificationManager.cancel(notificationId)
            logI("倒计时完成通知已取消")
        } catch (e: Exception) {
            logE("取消倒计时完成通知失败: ${e.message}")
        }
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
        logI("倒计时通知渠道已重新创建: $channelId")
    }

    fun canUseFullScreenIntent(): Boolean {
        val canUse = NotificationManagerCompat.from(context).canUseFullScreenIntent()
        logI("fullScreenIntent权限检查: canUse=$canUse, SDK=${Build.VERSION.SDK_INT}")
        return canUse
    }
}
