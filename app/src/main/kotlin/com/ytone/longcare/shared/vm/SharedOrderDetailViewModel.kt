package com.ytone.longcare.shared.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.features.location.core.LocationFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val unifiedOrderRepository: UnifiedOrderRepository,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val locationFacade: LocationFacade,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    // 当前订单信息请求
    private val _currentOrderId = MutableStateFlow<OrderInfoRequestModel?>(null)
    val currentOrderId: StateFlow<OrderInfoRequestModel?> = _currentOrderId.asStateFlow()

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

            when (val result = unifiedOrderRepository.getOrderInfo(request.toOrderKey(), forceRefresh)) {
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
     * @param request 订单信息请求模型
     * @return 缓存的订单详情，如果不存在则返回null
     */
    fun getCachedOrderInfo(request: OrderInfoRequestModel): ServiceOrderInfoModel? {
        return unifiedOrderRepository.getCachedOrderInfo(request.toOrderKey())
    }

    /**
     * 预加载订单详情
     * @param request 订单信息请求模型
     */
    fun preloadOrderInfo(request: OrderInfoRequestModel) {
        viewModelScope.launch {
            unifiedOrderRepository.preloadOrderInfo(request.toOrderKey())
        }
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
     * @param request 订单信息请求模型
     * @return 项目ID列表
     */
    fun getProjectIdList(request: OrderInfoRequestModel): List<Int> {
        return getCachedOrderInfo(request)?.projectList?.map { it.projectId } ?: emptyList()
    }

    /**
     * 清除指定订单的缓存
     * @param request 订单信息请求模型
     */
    fun clearOrderCache(request: OrderInfoRequestModel) {
        unifiedOrderRepository.clearOrderInfoCache(request.toOrderKey())
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
     * 获取当前位置坐标
     * @return 经纬度对，失败时返回空字符串
     */
    private suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            // 检查定位权限
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                // 权限未授予，返回空字符串
                return Pair("", "")
            }
            
            // 检查定位服务是否开启
            if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                // 定位服务未开启，返回空字符串
                return Pair("", "")
            }
            
            val location = locationFacade.getCurrentLocation()
            if (location != null) {
                Pair(location.longitude.toString(), location.latitude.toString())
            } else {
                Pair("", "")
            }
        } catch (_: Exception) {
            // 记录异常但不抛出，返回空字符串
            Pair("", "")
        }
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

            // 获取当前位置坐标
            val (longitude, latitude) = getCurrentLocationCoordinates()

            when (val result = orderRepository.starOrder(request.orderId, selectedProjectIds, longitude, latitude)) {
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
