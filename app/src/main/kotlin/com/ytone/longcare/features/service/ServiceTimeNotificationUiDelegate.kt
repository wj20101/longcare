package com.ytone.longcare.features.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal class ServiceTimeNotificationUiDelegate(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val channelId: String,
    private val notificationIdSeed: Int,
) {
    fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("服务时间结束提醒")
            .setDescription("服务时间结束时的重要提醒通知")
            .setVibrationEnabled(true)
            .setLightsEnabled(true)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun showServiceTimeEndNotification(orderId: Long, serviceName: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("服务时间提醒")
            .setContentText("$serviceName 服务时间即将结束，请及时处理")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(buildNotificationId(orderId), notification)
    }

    private fun buildNotificationId(orderId: Long): Int {
        val positiveHash = ((orderId xor (orderId ushr 32)).toInt() and INT_POSITIVE_MASK)
        val range = Int.MAX_VALUE - notificationIdSeed
        return notificationIdSeed + (positiveHash % range)
    }

    private companion object {
        private const val INT_POSITIVE_MASK = 0x7fffffff
    }
}
