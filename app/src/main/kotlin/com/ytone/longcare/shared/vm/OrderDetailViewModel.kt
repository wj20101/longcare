package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toRequestModel
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
    private val unifiedOrderRepository: UnifiedOrderRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()
    
    // 选中的项目ID列表
    private val _selectedProjectIds = MutableStateFlow<List<Int>>(emptyList())
    val selectedProjectIds: StateFlow<List<Int>> = _selectedProjectIds.asStateFlow()

    /**
     * 获取订单详情
     * @param orderKey 订单标识符
     */
    fun getOrderInfo(orderKey: OrderKey) {
        val request = orderKey.toRequestModel()
        getOrderInfo(request)
    }
    
    /**
     * 获取订单详情
     * @param request 订单详情请求参数
     */
    fun getOrderInfo(request: OrderInfoRequestModel) {
        viewModelScope.launch {
            _uiState.value = OrderDetailUiState.Loading

            when (val result = orderRepository.getOrderInfo(request)) {
                is ApiResult.Success -> {
                    _uiState.value = OrderDetailUiState.Success(result.data)
                    // 同时加载选中的项目ID
                    loadSelectedProjectIds(request.orderId)
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
    
    /**
     * 从Room加载选中的项目ID
     */
    private suspend fun loadSelectedProjectIds(orderId: Long) {
        val ids = unifiedOrderRepository.getSelectedProjectIds(OrderKey(orderId))
        _selectedProjectIds.value = ids
    }
    
    /**
     * 清除选中的项目（服务完成时调用）
     */
    fun clearSelectedProjects(orderId: Long) {
        viewModelScope.launch {
            unifiedOrderRepository.updateSelectedProjects(OrderKey(orderId), emptyList())
            _selectedProjectIds.value = emptyList()
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