package com.ytone.longcare.features.servicecountdown.service

import android.content.Context
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCountdownSystemGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val countdownNotificationManager: CountdownNotificationManager,
    private val countdownTaskCodec: CountdownTaskCodec,
) : ServiceCountdownSystemGateway, SessionRuntimeCleanupHook {

    override fun startForegroundService(
        orderKey: OrderKey,
        serviceName: String,
        totalSeconds: Long,
    ) {
        val payload = countdownNotificationManager.captureCurrentPayload(
            orderKey = orderKey,
            serviceName = serviceName,
            triggerAtMillis = System.currentTimeMillis() + totalSeconds * 1_000L,
        ) ?: return
        CountdownForegroundService.startCountdown(
            context = context,
            payload = payload,
            totalSeconds = totalSeconds,
            codec = countdownTaskCodec,
        )
    }

    override fun stopForegroundService() {
        CountdownForegroundService.stopCountdown(context)
    }

    override fun stopAlarmRingtone() {
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

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        stopForegroundService()
        stopAlarmRingtone()
        countdownNotificationManager.cleanup(identity)
    }
}
