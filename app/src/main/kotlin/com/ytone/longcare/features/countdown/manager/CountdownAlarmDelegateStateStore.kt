package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ytone.longcare.common.utils.klogE
import com.ytone.longcare.common.utils.klogI

internal fun logNextAlarmClock(alarmManager: AlarmManager) {
    try {
        val nextAlarmClock = alarmManager.nextAlarmClock
        if (nextAlarmClock != null) {
            klogI("当前系统下一个闹钟时间: ${nextAlarmClock.triggerTime}")
        }
    } catch (exception: Exception) {
        klogE("读取系统下一个闹钟失败", throwable = exception)
    }
}

internal fun saveLastScheduledOrderId(
    prefs: SharedPreferences,
    keyLastScheduledOrderId: String,
    orderId: Long
) {
    prefs.edit {
        putLong(keyLastScheduledOrderId, orderId)
    }
}

internal fun getLastScheduledOrderId(
    prefs: SharedPreferences,
    keyLastScheduledOrderId: String,
    noOrderId: Long
): Long = prefs.getLong(keyLastScheduledOrderId, noOrderId)

internal fun clearLastScheduledOrderId(
    prefs: SharedPreferences,
    keyLastScheduledOrderId: String
) {
    prefs.edit {
        remove(keyLastScheduledOrderId)
    }
}
