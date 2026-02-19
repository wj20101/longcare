package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver

internal fun cancelCountdownAlarmForOrderId(
    context: Context,
    alarmManager: AlarmManager,
    countdownAlarmRequestCode: Int,
    actionCountdownAlarmPrefix: String,
    orderId: Long
) {
    val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
        action = "$actionCountdownAlarmPrefix$orderId"
    }
    val pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        countdownAlarmRequestCode,
        intent,
        PendingIntent.FLAG_NO_CREATE,
        false
    )
    if (pendingIntent == null) {
        klogI("订单 $orderId 的倒计时闹钟不存在，跳过取消")
        return
    }
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
    klogI("✅ 订单 $orderId 的倒计时闹钟已取消")
}

internal fun cancelGenericCountdownAlarm(
    context: Context,
    alarmManager: AlarmManager,
    countdownAlarmRequestCode: Int
): Boolean {
    val intent = Intent(context, CountdownAlarmReceiver::class.java)
    val pendingIntent = PendingIntentCompat.getBroadcast(
        context,
        countdownAlarmRequestCode,
        intent,
        PendingIntent.FLAG_NO_CREATE,
        false
    )
    if (pendingIntent == null) {
        klogI("未找到通用倒计时闹钟PendingIntent，跳过通用取消")
        return false
    }
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
    klogI("✅ 倒计时闹钟已取消 (通过通用PendingIntent)")
    return true
}
