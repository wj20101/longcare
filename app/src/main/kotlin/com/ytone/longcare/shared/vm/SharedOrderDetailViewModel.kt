package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.api.request.OrderInfoRequestModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 共享的订单详情ViewModel
 * 用于在多个页面间共享订单详情数据和状态
 */
@HiltViewModel
class SharedOrderDetailViewModel @Inject constructor(
    private val sharedOrderRepository: SharedOrderRepository,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    // 当前订单信息请求
    private val _currentOrderId = MutableStateFlow<OrderInfoRequestModel?>(null)
    val currentOrderId: StateFlow<OrderInfoRequestModel?> = _currentOrderId.asStateFlow()

    /**
     * 获取订单详情
     * @param orderId 订单ID
     * @param forceRefresh 是否强制刷新
     */
    fun getOrderInfo(orderId: Long, forceRefresh: Boolean = false) {
        getOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0), forceRefresh)
    }

    /**
     * 获取订单详情
     * @param request 订单信息请求模型
     * @param forceRefresh 是否强制刷新
     */
    fun getOrderInfo(request: OrderInfoRequestModel, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // 如果是同一个订单且不强制刷新，且当前状态是成功状态，则不重复请求
            if (!forceRefresh && 
                _currentOrderId.value == request && 
                _uiState.value is OrderDetailUiState.Success) {
                return@launch
            }

            _currentOrderId.value = request
            _uiState.value = OrderDetailUiState.Loading

            when (val result = sharedOrderRepository.getOrderInfo(request, forceRefresh)) {
                is ApiResult.Success -> {
                    _uiState.value = OrderDetailUiState.Success(result.data)
                }
                is ApiResult.Exception -> {
                    val errorMessage = result.exception.message ?: "网络错误，请检查网络连接"
                    _uiState.value = OrderDetailUiState.Error(errorMessage)
                    toastHelper.showShort(errorMessage)
                }
                is ApiResult.Failure -> {
                    _uiState.value = OrderDetailUiState.Error(result.message)
                    toastHelper.showShort(result.message)
                }
            }
        }
    }

    /**
     * 获取缓存的订单详情
     * @param orderId 订单ID
     * @return 缓存的订单详情，如果不存在则返回null
     */
    fun getCachedOrderInfo(orderId: Long): ServiceOrderInfoModel? {
        return getCachedOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
    }

    /**
     * 获取缓存的订单详情
     * @param request 订单信息请求模型
     * @return 缓存的订单详情，如果不存在则返回null
     */
    fun getCachedOrderInfo(request: OrderInfoRequestModel): ServiceOrderInfoModel? {
        return sharedOrderRepository.getCachedOrderInfo(request)
    }

    /**
     * 预加载订单详情
     * @param orderId 订单ID
     */
    fun preloadOrderInfo(orderId: Long) {
        preloadOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
    }

    /**
     * 预加载订单详情
     * @param request 订单信息请求模型
     */
    fun preloadOrderInfo(request: OrderInfoRequestModel) {
        viewModelScope.launch {
            sharedOrderRepository.preloadOrderInfo(request)
        }
    }

    /**
     * 获取用户地址
     * @param orderId 订单ID
     * @return 用户地址，如果获取失败则返回空字符串
     */
    fun getUserAddress(orderId: Long): String {
        return getUserAddress(OrderInfoRequestModel(orderId = orderId, planId = 0))
    }

    /**
     * 获取用户地址
     * @param request 订单信息请求模型
     * @return 用户地址，如果获取失败则返回空字符串
     */
    fun getUserAddress(request: OrderInfoRequestModel): String {
        return getCachedOrderInfo(request)?.userInfo?.address ?: ""
    }

    /**
     * 获取项目ID列表
     * @param orderId 订单ID
     * @return 项目ID列表
     */
    fun getProjectIdList(orderId: Long): List<Int> {
        return getProjectIdList(OrderInfoRequestModel(orderId = orderId, planId = 0))
    }

    /**
     * 获取项目ID列表
     * @param request 订单信息请求模型
     * @return 项目ID列表
     */
    fun getProjectIdList(request: OrderInfoRequestModel): List<Int> {
        return getCachedOrderInfo(request)?.projectList?.map { it.projectId } ?: emptyList()
    }

    /**
     * 清除指定订单的缓存
     * @param orderId 订单ID
     */
    fun clearOrderCache(orderId: Long) {
        clearOrderCache(OrderInfoRequestModel(orderId = orderId, planId = 0))
    }

    /**
     * 清除指定订单的缓存
     * @param request 订单信息请求模型
     */
    fun clearOrderCache(request: OrderInfoRequestModel) {
        sharedOrderRepository.clearOrderCache(request)
        if (_currentOrderId.value == request) {
            _uiState.value = OrderDetailUiState.Initial
            _currentOrderId.value = null
        }
    }

    /**
     * 刷新当前订单详情
     */
    fun refreshCurrentOrder() {
        _currentOrderId.value?.let { request ->
            getOrderInfo(request, forceRefresh = true)
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _uiState.value = OrderDetailUiState.Initial
        _currentOrderId.value = null
    }

    // 工单开始状态
    private val _starOrderState = MutableStateFlow<StarOrderUiState>(StarOrderUiState.Initial)
    val starOrderState: StateFlow<StarOrderUiState> = _starOrderState.asStateFlow()

    /**
     * 工单开始(正式计时)
     * @param orderId 订单ID
     * @param selectedProjectIds 选中的项目ID列表
     * @param onSuccess 成功回调
     */
    fun starOrder(orderId: Long, selectedProjectIds: List<Long> = emptyList(), onSuccess: () -> Unit = {}) {
        starOrder(OrderInfoRequestModel(orderId = orderId, planId = 0), selectedProjectIds, onSuccess)
    }

    /**
     * 工单开始(正式计时)
     * @param request 订单信息请求模型
     * @param selectedProjectIds 选中的项目ID列表
     * @param onSuccess 成功回调
     */
    fun starOrder(request: OrderInfoRequestModel, selectedProjectIds: List<Long> = emptyList(), onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _starOrderState.value = StarOrderUiState.Loading

            when (val result = orderRepository.starOrder(request.orderId, selectedProjectIds)) {
                is ApiResult.Success -> {
                    _starOrderState.value = StarOrderUiState.Success
                    toastHelper.showShort("工单开始成功")
                    onSuccess()
                }
                is ApiResult.Exception -> {
                    val errorMessage = result.exception.message ?: "网络错误，请检查网络连接"
                    _starOrderState.value = StarOrderUiState.Error(errorMessage)
                    toastHelper.showShort(errorMessage)
                }
                is ApiResult.Failure -> {
                    _starOrderState.value = StarOrderUiState.Error(result.message)
                    toastHelper.showShort(result.message)
                }
            }
        }
    }

    /**
     * 重置工单开始状态
     */
    fun resetStarOrderState() {
        _starOrderState.value = StarOrderUiState.Initial
    }
}

/**
 * 工单开始UI状态
 */
sealed class StarOrderUiState {
    data object Initial : StarOrderUiState()
    data object Loading : StarOrderUiState()
    data object Success : StarOrderUiState()
    data class Error(val message: String) : StarOrderUiState()
}