package com.ytone.longcare.features.nursing.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.domain.order.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ytone.longcare.core.ui.R as CoreUiR

@HiltViewModel
internal class NursingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _orderListState = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
    val orderListState: StateFlow<List<ServiceOrderModel>> = _orderListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    /**
     * 获取指定日期的订单列表
     * @param daytime 查询日期，格式例如: "yyyy-MM-dd"
     */
    fun getOrderList(daytime: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = orderRepository.getOrderList(daytime)) {
                is ApiResult.Success -> {
                    _orderListState.value = result.data
                }

                is ApiResult.Failure -> {
                    messageQueue.enqueue(result.message)
                    _orderListState.value = emptyList()
                }

                is ApiResult.Exception -> {
                    messageQueue.enqueue(CoreUiR.string.common_network_error_retry)
                    _orderListState.value = emptyList()
                }
            }
            _isLoading.value = false
        }
    }
}
