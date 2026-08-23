package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ytone.longcare.core.ui.R

@HiltViewModel
class TodayOrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    // 创建一个 StateFlow 用于存放今日订单列表的UI状态
    private val _todayOrderListState = MutableStateFlow<List<TodayServiceOrderModel>>(emptyList())
    val todayOrderListState: StateFlow<List<TodayServiceOrderModel>> = _todayOrderListState.asStateFlow()

    // 创建一个 StateFlow 用于存放服务中订单列表的UI状态
    private val _inOrderListState = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
    val inOrderListState: StateFlow<List<ServiceOrderModel>> = _inOrderListState.asStateFlow()
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    fun loadTodayOrders() {
        viewModelScope.launch {
            when (val result = orderRepository.getTodayOrderList()) {
                is ApiResult.Success -> {
                    // 请求成功，更新状态
                    _todayOrderListState.value = result.data
                }

                is ApiResult.Failure -> {
                    // 请求失败或异常，将列表清空
                    _todayOrderListState.value = emptyList()
                    messageQueue.enqueue(result.message)
                }

                is ApiResult.Exception -> {
                    messageQueue.enqueue(R.string.common_network_error_retry)
                    logE(message = "今日订单请求接口失败", throwable = result.exception)
                }
            }
        }
    }

    fun loadInOrders() {
        viewModelScope.launch {
            when (val result = orderRepository.getInOrderList()) {
                is ApiResult.Success -> {
                    // 请求成功，更新状态
                    _inOrderListState.value = result.data
                }

                is ApiResult.Failure -> {
                    // 请求失败或异常，将列表清空
                    _inOrderListState.value = emptyList()
                    messageQueue.enqueue(result.message)
                }

                is ApiResult.Exception -> {
                    messageQueue.enqueue(R.string.common_network_error_retry)
                    logE(message = "服务中订单请求接口失败", throwable = result.exception)
                }
            }
        }
    }

}
