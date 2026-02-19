package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.common.utils.klogE
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity

internal data class CountdownScheduleMetadata(
    val useAlarmClock: Boolean,
    val nextAlarmTime: Long?
)

internal fun scheduleCountdownAlarmInSystem(
    context: Context,
    alarmManager: AlarmManager,
    countdownAlarmRequestCode: Int,
    countdownAlarmActivityRequestCode: Int,
    actionCountdownAlarmPrefix: String,
    orderKey: OrderKey,
    serviceName: String,
    triggerTimeMillis: Long,
    canUseExactAlarm: Boolean
): CountdownScheduleMetadata? {
    val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
        putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
        putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
        action = "$actionCountdownAlarmPrefix${orderKey.orderId}"
    }

    val pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        countdownAlarmRequestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    ) ?: run {
        klogE("❌ 创建倒计时闹钟 PendingIntent 失败")
        return null
    }

    val alarmActivityIntent = CountdownAlarmActivity.createIntent(
        context,
        orderKey,
        serviceName,
        autoCloseEnabled = false
    )
    val alarmActivityPendingIntent = PendingIntentCompat.getActivity(
        context,
        countdownAlarmActivityRequestCode,
        alarmActivityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    )

    val shouldUseAlarmClock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canUseExactAlarm
    if (shouldUseAlarmClock && alarmActivityPendingIntent != null) {
        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            triggerTimeMillis,
            alarmActivityPendingIntent
        )
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        klogI("✅ 通过AlarmClock设置倒计时闹钟(确保锁屏提醒): orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (shouldUseAlarmClock && alarmActivityPendingIntent == null) {
            klogE("⚠️ AlarmClock 所需 Activity PendingIntent 为空，降级为 setAndAllowWhileIdle")
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
        klogI("✅ 无精确闹钟能力，降级为 setAndAllowWhileIdle: orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
    } else {
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
        klogI("✅ 通过ExactAndAllowWhileIdle设置倒计时闹钟: orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
    }

    return CountdownScheduleMetadata(
        useAlarmClock = shouldUseAlarmClock,
        nextAlarmTime = alarmManager.nextAlarmClock?.triggerTime
    )
}
