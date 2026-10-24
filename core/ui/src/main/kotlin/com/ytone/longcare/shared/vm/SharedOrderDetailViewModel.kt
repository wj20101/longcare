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
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.time.ServiceTimeProvider
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
    private val serviceLifecycle: ServiceOrderLifecycle,
    private val serviceTimeProvider: ServiceTimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Initial)
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    // 当前订单信息请求
    private var currentOrderKey: OrderKey? = null
    private var orderInfoJob: Job? = null

    fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean = false) {
        if (startingOrder != null && startingOrder != orderKey) cancelStartingOrder()
        if (!forceRefresh && currentOrderKey == orderKey &&
            (_uiState.value is OrderDetailUiState.Success || orderInfoJob?.isActive == true)
        ) return

        orderInfoJob?.cancel()
        currentOrderKey = orderKey
        _uiState.value = OrderDetailUiState.Loading
        orderInfoJob = viewModelScope.launch {
            val result = unifiedOrderRepository.getOrderInfo(orderKey, forceRefresh)
            currentCoroutineContext().ensureActive()
            when (result) {
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

    fun getUserAddress(orderKey: OrderKey): String {
        return getCachedOrderInfo(orderKey)?.userInfo?.address ?: ""
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

    // 工单开始状态
    private val _starOrderState = MutableStateFlow<StarOrderUiState>(StarOrderUiState.Initial)
    val starOrderState: StateFlow<StarOrderUiState> = _starOrderState.asStateFlow()
    private var startingOrder: OrderKey? = null
    private var startJob: Job? = null

    private fun cancelStartingOrder() {
        val previousJob = startJob
        val previousOrder = startingOrder
        startJob = null
        startingOrder = null
        previousJob?.cancel()
        previousOrder?.let { serviceLifecycle.cancelOrderPreparation(it.orderId) }
        _starOrderState.value = StarOrderUiState.Initial
    }

    /**
     * 工单开始(正式计时)
     * @param orderKey 订单标识
     * @param selectedProjectIds 选中的项目ID列表
     */
    fun starOrder(orderKey: OrderKey, selectedProjectIds: List<Int> = emptyList()) {
        if (startJob?.isActive == true && startingOrder == orderKey) return
        if ((_starOrderState.value as? StarOrderUiState.Success)?.orderKey == orderKey) return
        cancelStartingOrder()
        val projectIds = selectedProjectIds.toList()
        startingOrder = orderKey
        _starOrderState.value = StarOrderUiState.Loading
        serviceLifecycle.prepareOrderStart(orderKey.orderId)
        startJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val userId = DiagnosticEventTracker.currentUserId()
                val context = mapOf("orderId" to orderKey.orderId, "planId" to orderKey.planId,
                    "signInMode" to "START_ORDER", "stage" to "service_start")
                val started = System.nanoTime()
                val acquisition = try {
                    locationFacade.acquireServiceOrderLocation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_exception",
                        "正式开始服务定位异常", error, context, userId)
                    _starOrderState.value = StarOrderUiState.Error(textResolver.text(LocationFailure.UNAVAILABLE.messageRes()))
                    return@launch
                }
                val acquisitionExtras = context + ("durationMs" to (System.nanoTime() - started) / 1_000_000)
                currentCoroutineContext().ensureActive()
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
                val locationFailure = locationFacade.serviceOrderLocationFailure(location)
                if (locationFailure != null) {
                    DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_location_rejected",
                        "正式开始服务位置不可用", extras = acquisitionExtras + locationDiagnosticExtras(location), userId = userId)
                    _starOrderState.value = StarOrderUiState.Error(textResolver.text(locationFailure.messageRes()))
                    return@launch
                }
                val longitude = location.longitude.toString()
                val latitude = location.latitude.toString()
                val diagnosticExtras = acquisitionExtras + locationDiagnosticExtras(location)
                DiagnosticEventTracker.trackEvent(DiagnosticCategory.LOCATION, "service_start_location_submit",
                    "正式开始服务提交位置", diagnosticExtras, userId, reportToServer = true)

                val result = orderRepository.starOrder(orderKey.orderId, projectIds.map(Int::toLong), longitude, latitude)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is ApiResult.Success -> {
                        // API 无服务端起点字段，以确认成功的本机时刻计时，不能等下一页加载完成。
                        val confirmedAt = serviceTimeProvider.snapshot()
                        DiagnosticEventTracker.trackEvent(DiagnosticCategory.LOCATION, "service_start_location_success",
                            "正式开始服务位置提交成功", diagnosticExtras, userId, reportToServer = true)
                        serviceLifecycle.onOrderStarted(orderKey.orderId)
                        try {
                            // 仅保护服务端已确认的本地 Room 写入，页面释放不能丢失计时起点。
                            withContext(NonCancellable) {
                                unifiedOrderRepository.startLocalService(orderKey, confirmedAt)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            DiagnosticEventTracker.trackError(DiagnosticCategory.COUNTDOWN, "service_start_time_save_failed",
                                "服务已开始，但本地计时起点保存失败", error, context, userId)
                        }
                        currentCoroutineContext().ensureActive()
                        try {
                            unifiedOrderRepository.updateSelectedProjects(orderKey, projectIds)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            // 服务端已经开始，磁盘失败不能提示重新签到；路由仍携带本次项目。
                            DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "service_start_projects_save_failed",
                                "服务已开始，但本地项目保存失败", error, context, userId)
                        }
                        currentCoroutineContext().ensureActive()
                        _starOrderState.value = StarOrderUiState.Success(orderKey, projectIds)
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
            } finally {
                // A cancelled SDK call may finish late; it must not release a newer preparation.
                if (startJob === currentCoroutineContext()[Job]) {
                    startJob = null
                    startingOrder = null
                    serviceLifecycle.cancelOrderPreparation(orderKey.orderId)
                    if (_starOrderState.value is StarOrderUiState.Loading) {
                        _starOrderState.value = StarOrderUiState.Initial
                    }
                }
            }
        }
        startJob?.start()
    }

    /** 仅消费当前展示的结果，旧页面或旧 Snackbar 不能清除后来的动作。 */
    fun acknowledgeStartResult(result: StarOrderUiState) {
        if ((result is StarOrderUiState.Success || result is StarOrderUiState.Error) &&
            _starOrderState.value === result
        ) {
            _starOrderState.value = StarOrderUiState.Initial
        }
    }
}

/**
 * 工单开始UI状态
 */
sealed class StarOrderUiState {
    data object Initial : StarOrderUiState()
    data object Loading : StarOrderUiState()
    data class Success(val orderKey: OrderKey, val selectedProjectIds: List<Int>) : StarOrderUiState()
    data class Error(val message: String) : StarOrderUiState()
}
