package com.ytone.longcare.features.endservice.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.order.CheckoutPhotos
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import javax.inject.Inject

/**
 * 结束服务选择页面的ViewModel
 */
@HiltViewModel
class EndServiceSelectionViewModel @Inject constructor(
    private val unifiedOrderRepository: OrderDetailRepository,
    private val textResolver: ResourceTextResolver,
    private val imageRepository: OrderImageRepository,
) : ViewModel() {

    // 页面状态
    private val _uiState = MutableStateFlow<EndServiceSelectionUiState>(EndServiceSelectionUiState.Loading)
    val uiState: StateFlow<EndServiceSelectionUiState> = _uiState.asStateFlow()

    // 选中的项目ID集合
    private val _selectedProjectIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedProjectIds: StateFlow<Set<Int>> = _selectedProjectIds.asStateFlow()

    // 所有的项目列表
    private val _projectList = MutableStateFlow<List<ServiceProjectM>>(emptyList())
    val projectList: StateFlow<List<ServiceProjectM>> = _projectList.asStateFlow()
    private val _isPreparingCheckout = MutableStateFlow(false)
    val isPreparingCheckout = _isPreparingCheckout.asStateFlow()
    private var activeOrder: OrderKey? = null
    private var initialization: Job? = null

    /**
     * 初始化数据
     * @param orderKey 订单键
     * @param initialSelectedIds 初始选中的项目ID列表（开始服务时选择的项目）
     */
    fun initData(orderKey: OrderKey, initialSelectedIds: List<Int>) {
        initialization?.cancel()
        activeOrder = orderKey
        _uiState.value = EndServiceSelectionUiState.Loading
        _selectedProjectIds.value = emptySet()
        _projectList.value = emptyList()
        initialization = viewModelScope.launch {
            // 尝试获取缓存的订单详情
            val cachedOrderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey)
            if (cachedOrderInfo != null) {
                // 只显示开始服务时选择的项目（过滤后的列表）
                val selectedProjects = (cachedOrderInfo.projectList ?: emptyList())
                    .filter { initialSelectedIds.contains(it.projectId) }
                _projectList.value = selectedProjects
                // 默认全部选中
                _selectedProjectIds.value = selectedProjects.map { it.projectId }.toSet()
                _uiState.value = EndServiceSelectionUiState.Success
            } else {
                // 如果没有缓存，尝试从网络加载
                val result = unifiedOrderRepository.getOrderInfo(orderKey)
                currentCoroutineContext().ensureActive()
                if (activeOrder != orderKey) return@launch
                when (result) {
                    is ApiResult.Success -> {
                        // 只显示开始服务时选择的项目（过滤后的列表）
                        val selectedProjects = (result.data.projectList ?: emptyList())
                            .filter { initialSelectedIds.contains(it.projectId) }
                        _projectList.value = selectedProjects
                        // 默认全部选中
                        _selectedProjectIds.value = selectedProjects.map { it.projectId }.toSet()
                        _uiState.value = EndServiceSelectionUiState.Success
                    }
                    is ApiResult.Failure -> {
                        _uiState.value = EndServiceSelectionUiState.Error(result.message)
                    }
                    is ApiResult.Exception -> {
                        DiagnosticEventTracker.trackError(
                            category = END_SERVICE_DIAGNOSTIC_CATEGORY,
                            event = "end_service_project_load_exception",
                            description = "结束服务项目列表加载异常",
                            throwable = result.exception,
                            extras = mapOf(
                                "orderId" to orderKey.orderId,
                                "planId" to orderKey.planId,
                                "initialSelectedCount" to initialSelectedIds.size,
                            ),
                        )
                        _uiState.value = EndServiceSelectionUiState.Error(
                            textResolver.text(R.string.end_service_project_load_failed),
                        )
                    }
                }
            }
        }
    }

    /**
     * 设置选中状态
     */
    fun setSelection(projectIds: Set<Int>) {
        if (_isPreparingCheckout.value) return
        _selectedProjectIds.value = projectIds
    }

    /**
     * 全选
     */
    fun selectAll() {
        if (_isPreparingCheckout.value) return
        _selectedProjectIds.value = _projectList.value.map { it.projectId }.toSet()
    }
    
    /**
     * 取消全选
     */
    fun deselectAll() {
        if (_isPreparingCheckout.value) return
        _selectedProjectIds.value = emptySet()
    }

    /**
     * 切换项目的选中状态
     * @param projectId 项目ID
     */
    fun toggleSelection(projectId: Int) {
        if (_isPreparingCheckout.value) return
        val currentSelected = _selectedProjectIds.value.toMutableSet()
        if (currentSelected.contains(projectId)) {
            currentSelected.remove(projectId)
        } else {
            currentSelected.add(projectId)
        }
        _selectedProjectIds.value = currentSelected
    }

    /**
     * 获取最终确认的选中项目ID列表
     */
    fun getConfirmedProjectIds(): List<Int> {
        return _selectedProjectIds.value.toList()
    }

    suspend fun prepareCheckout(orderKey: OrderKey, endType: Int): CheckoutPreparation? {
        if (_isPreparingCheckout.value || activeOrder != orderKey) return null
        val projectIds = getConfirmedProjectIds()
        if (projectIds.isEmpty()) return CheckoutPreparation.Rejected(textResolver.text(R.string.end_service_selection_required))
        _isPreparingCheckout.value = true
        try {
            val images = imageRepository.getImagesByOrderId(orderKey)
            currentCoroutineContext().ensureActive()
            if (activeOrder != orderKey) return null
            val photos = CheckoutPhotos.from(orderKey, images)
                ?: return CheckoutPreparation.Rejected(textResolver.text(R.string.service_checkout_photos_incomplete))
            return CheckoutPreparation.Ready(EndOderInfo(projectIds, photos.before, photos.center, photos.after, endType))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DiagnosticEventTracker.trackError(
                category = END_SERVICE_DIAGNOSTIC_CATEGORY,
                event = "end_service_photo_load_exception",
                description = "签退前读取服务照片失败",
                throwable = error,
                extras = mapOf("orderId" to orderKey.orderId, "planId" to orderKey.planId),
            )
            return CheckoutPreparation.Rejected(textResolver.text(R.string.service_checkout_photos_load_failed))
        } finally {
            _isPreparingCheckout.value = false
        }
    }

    private companion object {
        val END_SERVICE_DIAGNOSTIC_CATEGORY = DiagnosticCategory.END_SERVICE
    }
}

sealed interface CheckoutPreparation {
    data class Ready(val info: EndOderInfo) : CheckoutPreparation
    data class Rejected(val message: String) : CheckoutPreparation
}

sealed class EndServiceSelectionUiState {
    data object Loading : EndServiceSelectionUiState()
    data object Success : EndServiceSelectionUiState()
    data class Error(val message: String) : EndServiceSelectionUiState()
}
