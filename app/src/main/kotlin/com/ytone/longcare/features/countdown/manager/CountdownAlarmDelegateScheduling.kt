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
    codec: CountdownTaskCodec,
    payload: CountdownTaskPayload,
    canUseExactAlarm: Boolean
): CountdownScheduleMetadata? {
    val identity = payload.execution.taskIdentity
    val intent = codec.writeToIntent(
        Intent(context, CountdownAlarmReceiver::class.java),
        payload,
        CountdownIntentPurpose.ALARM,
    )

    val pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        codec.requestCode(identity, CountdownIntentPurpose.ALARM),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    ) ?: run {
        klogE("❌ 创建倒计时闹钟 PendingIntent 失败")
        return null
    }

    val alarmActivityIntent = CountdownAlarmActivity.createIntent(
        context,
        payload.orderKey,
        payload.serviceName,
        autoCloseEnabled = false,
    ).let {
        codec.writeToIntent(it, payload, CountdownIntentPurpose.ALARM_ACTIVITY)
    }
    val alarmActivityPendingIntent = PendingIntentCompat.getActivity(
        context,
        codec.requestCode(identity, CountdownIntentPurpose.ALARM_ACTIVITY),
        alarmActivityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    )

    val supportsAlarmClock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var useAlarmClock =
        supportsAlarmClock &&
            canUseExactAlarm &&
            alarmActivityPendingIntent != null
    if (useAlarmClock) {
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                payload.triggerAtMillis,
                alarmActivityPendingIntent
            )
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            klogI("✅ 通过AlarmClock设置用户隔离倒计时闹钟: task=${identity.encode()}")
        } catch (exception: SecurityException) {
            useAlarmClock = false
            klogE("⚠️ 精确闹钟权限在调度时不可用，降级为 setAndAllowWhileIdle: ${exception.message}")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                payload.triggerAtMillis,
                pendingIntent
            )
        }
    } else if (supportsAlarmClock) {
        if (!canUseExactAlarm) {
            klogI("⚠️ 无精确闹钟权限，降级为 setAndAllowWhileIdle")
        } else {
            klogE("⚠️ AlarmClock 所需 Activity PendingIntent 为空，降级为 setAndAllowWhileIdle")
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            payload.triggerAtMillis,
            pendingIntent
        )
        klogI("✅ 无精确闹钟能力，降级为 setAndAllowWhileIdle: task=${identity.encode()}")
    } else {
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            payload.triggerAtMillis,
            pendingIntent
        )
        klogI("✅ 通过ExactAndAllowWhileIdle设置倒计时闹钟: task=${identity.encode()}")
    }

    return CountdownScheduleMetadata(
        useAlarmClock = useAlarmClock,
        nextAlarmTime = runCatching { alarmManager.nextAlarmClock?.triggerTime }.getOrNull(),
    )
}
