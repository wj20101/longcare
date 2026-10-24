package com.ytone.longcare.features.servicecountdown.domain

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceCountdownDeadline

/**
 * 服务倒计时系统能力网关。
 * 封装闹钟与响铃平台能力，持续定位由定位业务独立管理。
 */
interface ServiceCountdownSystemGateway {
    fun canScheduleExactAlarms(): Boolean

    fun canUseFullScreenIntent(): Boolean

    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        deadline: ServiceCountdownDeadline,
    )

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey)
}
