package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.userstorage.UserTaskIdentity

internal class CountdownAlarmDelegate(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val codec: CountdownTaskCodec,
) {
    fun scheduleCountdownAlarm(payload: CountdownTaskPayload): Boolean =
        try {
            logI(
                "开始设置用户隔离倒计时闹钟: " +
                    "orderId=${payload.orderKey.orderId}, triggerTime=${payload.triggerAtMillis}",
            )
            val scheduleMetadata = scheduleCountdownAlarmInSystem(
                context = context,
                alarmManager = alarmManager,
                codec = codec,
                payload = payload,
                canUseExactAlarm = canScheduleExactAlarms(),
            ) ?: return false

            logI("下一个闹钟时间: ${scheduleMetadata.nextAlarmTime}")
            trackAlarmScheduleSuccess(
                orderId = payload.orderKey.orderId,
                serviceName = payload.serviceName,
                triggerTimeMillis = payload.triggerAtMillis,
                scheduleMetadata = scheduleMetadata,
            )
            true
        } catch (e: Exception) {
            logE("❌ 设置倒计时闹钟失败: ${e.message}", throwable = e)
            trackAlarmScheduleFailure(
                orderId = payload.orderKey.orderId,
                serviceName = payload.serviceName,
                triggerTimeMillis = payload.triggerAtMillis,
                error = e,
            )
            false
        }

    fun cancelCountdownAlarm(identity: UserTaskIdentity) {
        try {
            cancelCountdownAlarmForIdentity(context, alarmManager, codec, identity)
            logNextAlarmClock(alarmManager)
        } catch (e: Exception) {
            logE("❌ 取消倒计时闹钟失败: ${e.message}", throwable = e)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
