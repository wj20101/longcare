package com.ytone.longcare.features.nursing.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.domain.order.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ytone.longcare.core.ui.R as CoreUiR

data class NursingOrderListState(
    val daytime: String? = null,
    val orders: List<ServiceOrderModel> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class NursingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _orderListState = MutableStateFlow(NursingOrderListState())
    val orderListState: StateFlow<NursingOrderListState> = _orderListState.asStateFlow()

    private var requestJob: Job? = null
    private var requestGeneration = 0L
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    /**
     * 获取指定日期的订单列表
     * @param daytime 查询日期，格式例如: "yyyy-MM-dd"
     */
    fun getOrderList(daytime: String) {
        val generation = ++requestGeneration
        requestJob?.cancel()
        _orderListState.value = NursingOrderListState(daytime = daytime, isLoading = true)
        requestJob = viewModelScope.launch {
            try {
                val result = orderRepository.getOrderList(daytime)
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration) return@launch
                val orders = when (result) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> {
                        messageQueue.enqueue(result.message)
                        emptyList()
                    }
                    is ApiResult.Exception -> {
                        val exception = result.exception
                        if (exception is CancellationException) throw exception
                        messageQueue.enqueue(CoreUiR.string.common_network_error_retry)
                        emptyList()
                    }
                }
                _orderListState.value = NursingOrderListState(daytime = daytime, orders = orders)
            } finally {
                if (generation == requestGeneration) {
                    _orderListState.value = _orderListState.value.copy(isLoading = false)
                }
            }
        }
    }
}
