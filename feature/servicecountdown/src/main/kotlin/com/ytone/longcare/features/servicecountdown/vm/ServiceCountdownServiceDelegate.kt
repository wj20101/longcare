package com.ytone.longcare.features.servicecountdown.vm

import android.content.Context
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ServiceCountdownServiceDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val orderDetailRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val systemGateway: ServiceCountdownSystemGateway,
    private val viewModelScope: CoroutineScope,
) {
    fun startForegroundService(
        context: Context,
        orderKey: OrderKey,
        serviceName: String,
        totalSeconds: Long
    ) {
        systemGateway.startForegroundService(context, orderKey, serviceName, totalSeconds)
    }

    fun stopForegroundService(context: Context) {
        systemGateway.stopForegroundService(context)
    }

    fun endService(orderKey: OrderKey, context: Context? = null) {
        stateHolder.countdownJob?.cancel()
        stateHolder.orderStatePollingJob?.cancel()
        stateHolder.countdownState.value = ServiceCountdownState.ENDED

        context?.let {
            stopForegroundService(it)
            systemGateway.stopAlarmRingtone(it)
        }
        systemGateway.cancelCountdownAlarmForOrder(orderKey)
        viewModelScope.launch {
            orderDetailRepository.endLocalService(orderKey)
            imageRepository.deleteImagesByOrderId(orderKey)
        }
    }

    fun endServiceWithoutClearingImages(orderKey: OrderKey, context: Context? = null) {
        stateHolder.countdownJob?.cancel()
        stateHolder.orderStatePollingJob?.cancel()
        stateHolder.countdownState.value = ServiceCountdownState.ENDED
        context?.let { stopForegroundService(it) }
        viewModelScope.launch {
            orderDetailRepository.endLocalService(orderKey)
        }
    }

    fun canScheduleExactAlarms(): Boolean = systemGateway.canScheduleExactAlarms()

    fun canUseFullScreenIntent(): Boolean = systemGateway.canUseFullScreenIntent()

    fun scheduleCountdownAlarm(orderKey: OrderKey, serviceName: String, triggerTimeMillis: Long) {
        systemGateway.scheduleCountdownAlarm(orderKey, serviceName, triggerTimeMillis)
    }

    fun cancelCountdownAlarm() {
        systemGateway.cancelCountdownAlarm()
    }

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        systemGateway.cancelCountdownAlarmForOrder(orderKey)
    }

    fun onCleared() {
        stateHolder.countdownJob?.cancel()
        stateHolder.orderStatePollingJob?.cancel()
    }
}
