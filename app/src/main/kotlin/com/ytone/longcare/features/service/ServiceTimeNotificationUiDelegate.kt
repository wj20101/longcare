package com.ytone.longcare.features.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ytone.longcare.R

internal class ServiceTimeNotificationUiDelegate(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val channelId: String,
    private val taskCodec: ServiceTimeTaskCodec,
) {
    fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName(context.getString(R.string.service_time_notification_channel_name))
            .setDescription(
                context.getString(R.string.service_time_notification_channel_description),
            )
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun showServiceTimeEndNotification(
        taskIdentity: com.ytone.longcare.domain.userstorage.UserTaskIdentity,
        serviceName: String,
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.service_time_notification_title))
            .setContentText(
                context.getString(R.string.service_time_notification_content, serviceName),
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(taskCodec.notificationId(taskIdentity), notification)
    }

    fun cancel(taskIdentity: com.ytone.longcare.domain.userstorage.UserTaskIdentity) {
        notificationManager.cancel(taskCodec.notificationId(taskIdentity))
    }
}
