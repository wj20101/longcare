package com.ytone.longcare.features.countdown.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity

internal fun AlarmRingtoneService.launchAlarmActivityIfPossible(
    orderKey: OrderKey,
    serviceName: String
) {
    try {
        logI("AlarmRingtoneService: 尝试启动全屏 Activity (SDK=${Build.VERSION.SDK_INT})")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            logI("AlarmRingtoneService: Android 14+，跳过直接启动Activity，依赖fullScreenIntent")
            return
        }

        val alarmIntent = CountdownAlarmActivity.createIntent(
            this,
            orderKey,
            serviceName,
            autoCloseEnabled = false
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        val pendingIntent = PendingIntentCompat.getActivity(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        try {
            if (pendingIntent != null) {
                pendingIntent.send()
                logI("AlarmRingtoneService: ✅ 通过 PendingIntent 启动 Activity 成功")
            } else {
                logI("AlarmRingtoneService: PendingIntentCompat 返回 null，回退 direct startActivity")
                startActivity(alarmIntent)
                logI("AlarmRingtoneService: ✅ 通过 startActivity 启动 Activity 成功")
            }
        } catch (e: Exception) {
            logE("AlarmRingtoneService: PendingIntent 启动失败，尝试直接 startActivity: ${e.message}")
            startActivity(alarmIntent)
            logI("AlarmRingtoneService: ✅ 通过 startActivity 启动 Activity 成功")
        }
    } catch (e: Exception) {
        logI("AlarmRingtoneService: ⚠️ 直接启动Activity失败 (Android 10+正常现象，依赖fullScreenIntent) - ${e.message}")
    }
}
