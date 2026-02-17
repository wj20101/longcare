package com.ytone.longcare.features.servicecountdown.domain

import android.content.Context
import com.ytone.longcare.model.OrderKey

/**
 * 服务倒计时系统能力网关。
 * 统一封装前台服务、闹钟与响铃等平台能力，降低 ViewModel 与 app 实现细节耦合。
 */
interface ServiceCountdownSystemGateway {
    fun startForegroundService(
        context: Context,
        orderKey: OrderKey,
        serviceName: String,
        totalSeconds: Long,
    )

    fun stopForegroundService(context: Context)

    fun stopAlarmRingtone(context: Context)

    fun canScheduleExactAlarms(): Boolean

    fun canUseFullScreenIntent(): Boolean

    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long,
    )

    fun cancelCountdownAlarm()

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey)
}
