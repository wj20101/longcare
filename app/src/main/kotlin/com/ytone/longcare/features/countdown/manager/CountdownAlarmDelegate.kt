package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.model.OrderKey

internal class CountdownAlarmDelegate(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val prefs: SharedPreferences,
    private val countdownAlarmRequestCode: Int,
    private val countdownAlarmActivityRequestCode: Int,
    private val actionCountdownAlarmPrefix: String,
    private val keyLastScheduledOrderId: String,
    private val noOrderId: Long,
) {
    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        try {
            logI("开始设置倒计时闹钟: orderId=${orderKey.orderId}, serviceName=$serviceName, triggerTime=$triggerTimeMillis")

            cancelCountdownAlarm()

            val scheduleMetadata = scheduleCountdownAlarmInSystem(
                context,
                alarmManager,
                countdownAlarmRequestCode,
                countdownAlarmActivityRequestCode,
                actionCountdownAlarmPrefix,
                orderKey,
                serviceName,
                triggerTimeMillis,
                canScheduleExactAlarms()
            ) ?: return

            saveLastScheduledOrderId(
                prefs = prefs,
                keyLastScheduledOrderId = keyLastScheduledOrderId,
                orderId = orderKey.orderId
            )

            logI("下一个闹钟时间: ${scheduleMetadata.nextAlarmTime}")

            trackAlarmScheduleSuccess(
                orderId = orderKey.orderId,
                serviceName = serviceName,
                triggerTimeMillis = triggerTimeMillis,
                scheduleMetadata = scheduleMetadata
            )
        } catch (e: Exception) {
            logE("❌ 设置倒计时闹钟失败: ${e.message}", throwable = e)
            trackAlarmScheduleFailure(
                orderId = orderKey.orderId,
                serviceName = serviceName,
                triggerTimeMillis = triggerTimeMillis,
                error = e
            )
        }
    }

    fun cancelCountdownAlarm() {
        try {
            logI("开始取消倒计时闹钟...")

            val lastOrderId = getLastScheduledOrderId(
                prefs = prefs,
                keyLastScheduledOrderId = keyLastScheduledOrderId,
                noOrderId = noOrderId
            )
            if (lastOrderId != noOrderId) {
                cancelCountdownAlarmForOrderId(
                    context = context,
                    alarmManager = alarmManager,
                    countdownAlarmRequestCode = countdownAlarmRequestCode,
                    actionCountdownAlarmPrefix = actionCountdownAlarmPrefix,
                    orderId = lastOrderId
                )
                clearLastScheduledOrderId(
                    prefs = prefs,
                    keyLastScheduledOrderId = keyLastScheduledOrderId
                )
                return
            }

            if (cancelGenericCountdownAlarm(context, alarmManager, countdownAlarmRequestCode)) {
                logNextAlarmClock(alarmManager)
            }
        } catch (e: Exception) {
            logE("❌ 取消倒计时闹钟失败: ${e.message}", throwable = e)
        }
    }

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        try {
            logI("开始取消订单 ${orderKey.orderId} 的倒计时闹钟...")
            cancelCountdownAlarmForOrderId(
                context = context,
                alarmManager = alarmManager,
                countdownAlarmRequestCode = countdownAlarmRequestCode,
                actionCountdownAlarmPrefix = actionCountdownAlarmPrefix,
                orderId = orderKey.orderId
            )
            if (
                getLastScheduledOrderId(
                    prefs = prefs,
                    keyLastScheduledOrderId = keyLastScheduledOrderId,
                    noOrderId = noOrderId
                ) == orderKey.orderId
            ) {
                clearLastScheduledOrderId(
                    prefs = prefs,
                    keyLastScheduledOrderId = keyLastScheduledOrderId
                )
            }
        } catch (e: Exception) {
            logE("❌ 取消订单 ${orderKey.orderId} 的倒计时闹钟失败: ${e.message}", throwable = e)
            cancelCountdownAlarm()
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
