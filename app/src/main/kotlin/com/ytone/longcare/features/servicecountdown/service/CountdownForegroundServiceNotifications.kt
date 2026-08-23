package com.ytone.longcare.features.servicecountdown.service

import android.app.Notification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownAppLauncher

internal fun CountdownForegroundService.createCountdownNotification(
    channelId: String,
    serviceName: String,
    orderId: Long,
    appLauncher: ServiceCountdownAppLauncher
): Notification {
    val contentTitle = getString(R.string.service_countdown_notification_title)
    val contentText = getString(R.string.service_countdown_notification_content, serviceName)

    // 点击通知跳转由 app 壳层实现，避免与 MainActivity 直接耦合。
    val pendingIntent = appLauncher.createCountdownContentIntent(this, orderId)

    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(contentTitle)
        .setContentText(contentText)
        .setSmallIcon(R.mipmap.app_logo_round)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setAutoCancel(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    if (pendingIntent != null) {
        builder.setContentIntent(pendingIntent)
    } else {
        logI("CountdownForegroundService: PendingIntentCompat 返回 null，通知点击跳转不可用")
    }

    return builder.build()
}

internal fun CountdownForegroundService.createForegroundNotificationChannel(channelId: String) {
    val channel = NotificationChannelCompat.Builder(
        channelId,
        NotificationManagerCompat.IMPORTANCE_LOW
    )
        .setName(getString(R.string.service_countdown_notification_channel_name))
        .setDescription(getString(R.string.service_countdown_notification_channel_description))
        .setVibrationEnabled(false)
        .setLightsEnabled(false)
        .setShowBadge(false)
        .build()

    NotificationManagerCompat.from(this).createNotificationChannel(channel)
    logI("倒计时前台服务通知渠道已创建")
}
