package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ServiceCountdownOrderStatePollingDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val serviceOrderLifecycle: ServiceOrderLifecycle,
    private val viewModelScope: CoroutineScope,
) {
    fun startOrderStatePolling(orderKey: OrderKey) {
        stateHolder.orderStatePollingJob?.cancel()
        serviceOrderLifecycle.monitorOrder(orderKey.orderId)
        stateHolder.orderStatePollingJob = viewModelScope.launch {
            serviceOrderLifecycle.orderState.collect { orderState ->
                if (orderState?.orderId == orderKey.orderId && !orderState.isInProgress()) {
                    stateHolder.orderStateError.value = orderState
                }
            }
        }
    }

    fun stopOrderStatePolling() {
        // Only detach this screen. Business state synchronization survives navigation/background.
        stateHolder.orderStatePollingJob?.cancel()
        stateHolder.orderStatePollingJob = null
    }

    fun clearOrderStateError() {
        stateHolder.orderStateError.value = null
    }
}
