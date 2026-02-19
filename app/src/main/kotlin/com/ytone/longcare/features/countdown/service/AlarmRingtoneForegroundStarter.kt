package com.ytone.longcare.features.countdown.service

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey

internal class AlarmRingtoneForegroundStarter(
    private val service: AlarmRingtoneService,
    private val countdownNotificationManager: CountdownNotificationManager,
    private val notificationId: Int
) {
    fun startAsForeground(orderKey: OrderKey, serviceName: String) {
        val notification = countdownNotificationManager.buildCountdownCompletionNotification(
            orderKey,
            serviceName
        )

        val notificationManagerCompat = NotificationManagerCompat.from(service)
        val hasNotificationPermission = notificationManagerCompat.areNotificationsEnabled()

        logI("AlarmRingtoneService: 通知权限状态=$hasNotificationPermission")

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        logI("AlarmRingtoneService: 前台服务类型=$foregroundServiceType, SDK=${Build.VERSION.SDK_INT}")

        try {
            ServiceCompat.startForeground(
                service,
                notificationId,
                notification,
                foregroundServiceType
            )
            logI("AlarmRingtoneService: ✅ 前台服务启动成功 (ID=$notificationId)")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: ❌ ServiceCompat.startForeground失败: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (
                    ContextCompat.checkSelfPermission(
                        service,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManagerCompat.notify(notificationId, notification)
                }
            } else {
                notificationManagerCompat.notify(notificationId, notification)
            }
        } catch (e: SecurityException) {
            logE("AlarmRingtoneService: 通知权限被拒绝 - ${e.message}")
        }

        logI("AlarmRingtoneService: ✅ 已升级为前台服务并刷新通知 (ID=$notificationId)")
    }
}
