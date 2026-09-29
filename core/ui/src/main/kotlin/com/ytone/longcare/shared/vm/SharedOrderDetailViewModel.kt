package com.ytone.longcare.shared.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.diagnostics.locationDiagnosticExtras
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.common.utils.messageRes
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 共享的订单详情ViewModel
 * 用于在多个页面间共享订单详情数据和状态
 */
@HiltViewModel
class SharedOrderDetailViewModel @Inject constructor(
    private val unifiedOrderRepository: OrderDetailRepository,
    private val orderRepository: OrderRepository,
    private val locationFacade: LocationFacade,
    private val textResolver: ResourceTextResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    // 当前订单信息请求
    private val _currentOrderId = MutableStateFlow<OrderKey?>(null)
    val currentOrderId: StateFlow<OrderKey?> = _currentOrderId.asStateFlow()

    fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // 如果是同一个订单且不强制刷新，且当前状态是成功状态，则不重复请求
            if (!forceRefresh && 
                _currentOrderId.value == orderKey && 
                _uiState.value is OrderDetailUiState.Success) {
                return@launch
            }

            _currentOrderId.value = orderKey
            _uiState.value = OrderDetailUiState.Loading

            when (val result = unifiedOrderRepository.getOrderInfo(orderKey, forceRefresh)) {
                is ApiResult.Success -> {
                    _uiState.value = OrderDetailUiState.Success(result.data)
                }
                is ApiResult.Exception -> {
                    _uiState.value = OrderDetailUiState.Error(
                        textResolver.text(result.exception.orderDetailErrorMessage()),
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = OrderDetailUiState.Error(
                        result.message.ifBlank { textResolver.text(R.string.order_detail_load_failed) }
                    )
                }
            }
        }
    }

    fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? {
        return unifiedOrderRepository.getCachedOrderInfo(orderKey)
    }

    fun preloadOrderInfo(orderKey: OrderKey) {
        viewModelScope.launch {
            unifiedOrderRepository.preloadOrderInfo(orderKey)
        }
    }

    fun getUserAddress(orderKey: OrderKey): String {
        return getCachedOrderInfo(orderKey)?.userInfo?.address ?: ""
    }

    fun getProjectIdList(orderKey: OrderKey): List<Int> {
        return getCachedOrderInfo(orderKey)?.projectList?.map { it.projectId } ?: emptyList()
    }

    suspend fun getSelectedProjectIdsOrDefault(
        orderKey: OrderKey,
        projectList: List<ServiceProjectM>
    ): List<Int> {
        val savedProjectIds = unifiedOrderRepository.getSelectedProjectIds(orderKey)
        return if (savedProjectIds.isEmpty()) {
            projectList.map { it.projectId }
        } else {
            savedProjectIds
        }
    }

    fun clearOrderCache(orderKey: OrderKey) {
        unifiedOrderRepository.clearOrderInfoCache(orderKey)
        if (_currentOrderId.value == orderKey) {
            _uiState.value = OrderDetailUiState.Initial
            _currentOrderId.value = null
        }
    }

    /**
     * 刷新当前订单详情
     */
    fun refreshCurrentOrder() {
        _currentOrderId.value?.let { orderKey ->
            getOrderInfo(orderKey, forceRefresh = true)
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
        starOrder(OrderKey(orderId = orderId, planId = 0), selectedProjectIds, onSuccess)
    }

    fun starOrder(orderKey: OrderKey, selectedProjectIds: List<Long> = emptyList(), onSuccess: () -> Unit = {}) {
        if (_starOrderState.value is StarOrderUiState.Loading) return
        _starOrderState.value = StarOrderUiState.Loading
        viewModelScope.launch {
            val userId = DiagnosticEventTracker.currentUserId()
            val context = mapOf("orderId" to orderKey.orderId, "planId" to orderKey.planId,
                "signInMode" to "START_ORDER", "stage" to "service_start")
            val started = System.nanoTime()
            val acquisition = try {
                locationFacade.acquireCurrentLocation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_exception",
                    "正式开始服务定位异常", error, context, userId)
                _starOrderState.value = StarOrderUiState.Error(textResolver.text(LocationFailure.UNAVAILABLE.messageRes()))
                return@launch
            }
            val acquisitionExtras = context + ("durationMs" to (System.nanoTime() - started) / 1_000_000)
            val location = when (acquisition) {
                is LocationAcquisition.Success -> acquisition.location
                is LocationAcquisition.Failure -> {
                    DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_failed",
                        "正式开始服务定位失败", extras = acquisitionExtras + ("errorCode" to acquisition.reason.name) +
                            (acquisition.location?.let { locationDiagnosticExtras(it) } ?: emptyMap()), userId = userId)
                    _starOrderState.value = StarOrderUiState.Error(textResolver.text(acquisition.reason.messageRes()))
                    return@launch
                }
            }
            if (!locationFacade.isUsable(location)) {
                DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_rejected",
                    "正式开始服务位置不可用", extras = acquisitionExtras + locationDiagnosticExtras(location), userId = userId)
                _starOrderState.value = StarOrderUiState.Error(textResolver.text(LocationFailure.QUALITY.messageRes()))
                return@launch
            }
            val longitude = location.longitude.toString()
            val latitude = location.latitude.toString()
            val diagnosticExtras = acquisitionExtras + locationDiagnosticExtras(location)
            DiagnosticEventTracker.trackEvent(DiagnosticCategory.LOCATION, "service_start_location_submit",
                "正式开始服务提交位置", diagnosticExtras, userId, reportToServer = true)

            when (val result = orderRepository.starOrder(orderKey.orderId, selectedProjectIds, longitude, latitude)) {
                is ApiResult.Success -> {
                    DiagnosticEventTracker.trackEvent(DiagnosticCategory.LOCATION, "service_start_location_success",
                        "正式开始服务位置提交成功", diagnosticExtras, userId, reportToServer = true)
                    _starOrderState.value = StarOrderUiState.Success
                    onSuccess()
                }
                is ApiResult.Exception -> {
                    DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_submit_exception",
                        "正式开始服务位置提交异常", result.exception, diagnosticExtras, userId)
                    _starOrderState.value = StarOrderUiState.Error(
                        textResolver.text(R.string.common_network_error_retry),
                    )
                }
                is ApiResult.Failure -> {
                    DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_submit_failure",
                        "正式开始服务位置提交失败", extras = diagnosticExtras + mapOf("failureCode" to result.code,
                            "failureMessage" to result.message), userId = userId)
                    _starOrderState.value = StarOrderUiState.Error(result.message)
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
