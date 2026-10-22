package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.common.utils.klogE
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.MainActivity
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics

internal data class CountdownScheduleMetadata(
    val nextAlarmTime: Long?,
    val diagnostics: android.os.Bundle?,
)

internal fun scheduleCountdownAlarmInSystem(
    context: Context,
    alarmManager: AlarmManager,
    countdownAlarmRequestCode: Int,
    actionCountdownAlarmPrefix: String,
    orderKey: OrderKey,
    serviceName: String,
    triggerTimeMillis: Long,
    canUseExactAlarm: Boolean,
    owner: String? = null,
): CountdownScheduleMetadata? {
    val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
        putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
        putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
        action = "$actionCountdownAlarmPrefix${orderKey.orderId}"
        putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, owner)
        putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, triggerTimeMillis)
    }

    // AlarmClock 的 showIntent 是用户提前点击系统闹钟入口，不能打开“已结束”页面。
    val showIntent = PendingIntentCompat.getActivity(context, 3004,
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT, false)

    // AlarmClock is available since API 21, including all supported devices. Only the
    // exact-alarm permission check is Android 12+; older devices need the same visible alarm.
    val exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canUseExactAlarm
    val useAlarmClock = exactAlarmAllowed && showIntent != null
    val mode = if (useAlarmClock) "alarm_clock" else "inexact_allow_idle"
    val fallback = when {
        useAlarmClock -> null
        !exactAlarmAllowed -> "exact_permission_missing"
        else -> "show_intent_missing"
    }
    CountdownAlarmDiagnostics.scheduled(intent, mode, fallback, exactAlarmAllowed)
    var pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        countdownAlarmRequestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    ) ?: run {
        klogE("❌ 创建倒计时闹钟 PendingIntent 失败")
        return null
    }

    if (useAlarmClock) {
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                triggerTimeMillis,
                showIntent
            )
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            klogI("✅ 通过AlarmClock设置倒计时闹钟(确保锁屏提醒): orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
        } catch (exception: SecurityException) {
            CountdownAlarmDiagnostics.fallback(intent, "inexact_allow_idle", "exact_permission_revoked")
            // Update only diagnostic extras on the same PendingIntent identity after the fallback.
            pendingIntent = PendingIntentCompat.getBroadcast(context, countdownAlarmRequestCode,
                intent, PendingIntent.FLAG_UPDATE_CURRENT, false) ?: pendingIntent
            klogE("⚠️ 精确闹钟权限在调度时不可用，降级为 setAndAllowWhileIdle: ${exception.message}")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        }
    } else {
        if (!exactAlarmAllowed) {
            klogI("⚠️ 无精确闹钟权限，降级为 setAndAllowWhileIdle")
        } else {
            klogE("⚠️ AlarmClock 所需 Activity PendingIntent 为空，降级为 setAndAllowWhileIdle")
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
        klogI("✅ 无精确闹钟能力，降级为 setAndAllowWhileIdle: orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")
    }

    return CountdownScheduleMetadata(
        nextAlarmTime = alarmManager.nextAlarmClock?.triggerTime,
        diagnostics = CountdownAlarmDiagnostics.read(intent)
    )
}
