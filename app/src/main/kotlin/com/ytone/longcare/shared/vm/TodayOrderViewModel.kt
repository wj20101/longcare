package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.order.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayOrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    // 创建一个 StateFlow 用于存放今日订单列表的UI状态
    private val _todayOrderListState = MutableStateFlow<List<TodayServiceOrderModel>>(emptyList())
    val todayOrderListState: StateFlow<List<TodayServiceOrderModel>> = _todayOrderListState.asStateFlow()

    // 创建一个 StateFlow 用于存放服务中订单列表的UI状态
    private val _inOrderListState = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
    val inOrderListState: StateFlow<List<ServiceOrderModel>> = _inOrderListState.asStateFlow()

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
                    // 这里可以添加错误处理逻辑，例如通过另一个 StateFlow 显示Toast
                    toastHelper.showShort(result.message)
                }

                is ApiResult.Exception -> {
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
                    // 这里可以添加错误处理逻辑，例如通过另一个 StateFlow 显示Toast
                    toastHelper.showShort(result.message)
                }

                is ApiResult.Exception -> {
                    logE(message = "服务中订单请求接口失败", throwable = result.exception)
                }
            }
        }
    }

}