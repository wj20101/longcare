package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    /**
     * 获取订单详情
     */
    fun getOrderInfo(orderId: Long) {
        viewModelScope.launch {
            _uiState.value = OrderDetailUiState.Loading

            when (val result = orderRepository.getOrderInfo(orderId)) {
                is ApiResult.Success -> {
                    _uiState.value = OrderDetailUiState.Success(result.data)
                }

                is ApiResult.Exception -> {
                    _uiState.value = OrderDetailUiState.Error(
                        result.exception.message ?: "未知错误"
                    )
                }

                is ApiResult.Failure -> {
                    toastHelper.showShort(result.message)
                    _uiState.value = OrderDetailUiState.Error(
                        result.message
                    )
                }
            }
        }
    }

}

/**
 * UI状态密封类
 */
sealed class OrderDetailUiState {
    data object Loading : OrderDetailUiState()
    data class Success(val orderInfo: ServiceOrderInfoModel) : OrderDetailUiState()
    data class Error(val message: String) : OrderDetailUiState()
    data object Initial : OrderDetailUiState()
}