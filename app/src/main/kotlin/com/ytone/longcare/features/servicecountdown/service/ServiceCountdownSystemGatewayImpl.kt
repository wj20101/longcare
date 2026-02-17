package com.ytone.longcare.features.servicecountdown.service

import android.content.Context
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCountdownSystemGatewayImpl @Inject constructor(
    private val countdownNotificationManager: CountdownNotificationManager,
) : ServiceCountdownSystemGateway {

    override fun startForegroundService(
        context: Context,
        orderKey: OrderKey,
        serviceName: String,
        totalSeconds: Long,
    ) {
        CountdownForegroundService.startCountdown(context, orderKey, serviceName, totalSeconds)
    }

    override fun stopForegroundService(context: Context) {
        CountdownForegroundService.stopCountdown(context)
    }

    override fun stopAlarmRingtone(context: Context) {
        AlarmRingtoneService.stopRingtone(context)
    }

    override fun canScheduleExactAlarms(): Boolean {
        return countdownNotificationManager.canScheduleExactAlarms()
    }

    override fun canUseFullScreenIntent(): Boolean {
        return countdownNotificationManager.canUseFullScreenIntent()
    }

    override fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long,
    ) {
        countdownNotificationManager.scheduleCountdownAlarm(
            orderKey = orderKey,
            serviceName = serviceName,
            triggerTimeMillis = triggerTimeMillis,
        )
    }

    override fun cancelCountdownAlarm() {
        countdownNotificationManager.cancelCountdownAlarm()
    }

    override fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        countdownNotificationManager.cancelCountdownAlarmForOrder(orderKey)
    }
}
