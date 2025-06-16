package com.ytone.longcare.features.nursing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.ServiceOrderModel
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
class NursingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _orderListState = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
    val orderListState: StateFlow<List<ServiceOrderModel>> = _orderListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                    toastHelper.showShort(result.message)
                    // 处理错误，可以添加错误状态或显示Toast
                    _orderListState.value = emptyList()
                }

                is ApiResult.Exception -> {
                    // 处理错误，可以添加错误状态或显示Toast
                    _orderListState.value = emptyList()
                }
            }
            _isLoading.value = false
        }
    }
}