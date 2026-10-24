package com.ytone.longcare.features.servicecountdown.service

import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceCountdownDeadline
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCountdownSystemGatewayImpl @Inject constructor(
    private val countdownNotificationManager: CountdownNotificationManager,
) : ServiceCountdownSystemGateway {

    override fun canScheduleExactAlarms(): Boolean {
        return countdownNotificationManager.canScheduleExactAlarms()
    }

    override fun canUseFullScreenIntent(): Boolean {
        return countdownNotificationManager.canUseFullScreenIntent()
    }

    override fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        deadline: ServiceCountdownDeadline,
    ) {
        countdownNotificationManager.scheduleCountdownAlarm(
            orderKey = orderKey,
            serviceName = serviceName,
            triggerTimeMillis = deadline.wallTimeMillis,
            deadline = deadline,
        )
    }

    override fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        countdownNotificationManager.cancelCountdownAlarmForOrder(orderKey)
    }
}
