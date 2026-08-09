package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ServiceCountdownOrderStatePollingDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val orderRepository: OrderRepository,
    private val viewModelScope: CoroutineScope,
    private val pollingIntervalMillis: Long = 5000L,
) {
    fun startOrderStatePolling(orderKey: OrderKey) {
        stateHolder.orderStatePollingJob?.cancel()
        stateHolder.orderStatePollingJob = viewModelScope.launch {
            while (isActive) {
                delay(pollingIntervalMillis)
                if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) {
                    break
                }
                when (val result = orderRepository.getOrderState(orderKey.orderId)) {
                    is ApiResult.Success -> {
                        val orderState = result.data
                        if (!orderState.isInProgress()) {
                            stateHolder.orderStateError.value = orderState
                            break
                        }
                    }
                    is ApiResult.Failure -> {
                        logI("查询订单状态失败: ${result.message}", tag = "ServiceCountdownViewModel")
                    }
                    is ApiResult.Exception -> {
                        logI("查询订单状态异常: ${result.exception.message}", tag = "ServiceCountdownViewModel")
                    }
                }
            }
        }
    }

    fun stopOrderStatePolling() {
        stateHolder.orderStatePollingJob?.cancel()
        stateHolder.orderStatePollingJob = null
    }

    fun clearOrderStateError() {
        stateHolder.orderStateError.value = null
    }
}
