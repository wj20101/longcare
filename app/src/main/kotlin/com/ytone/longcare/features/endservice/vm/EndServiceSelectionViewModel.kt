package com.ytone.longcare.features.endservice.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.order.SharedOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 结束服务选择页面的ViewModel
 */
@HiltViewModel
class EndServiceSelectionViewModel @Inject constructor(
    private val sharedOrderRepository: SharedOrderRepository
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

    /**
     * 初始化数据
     * @param orderInfoRequest 订单信息请求
     * @param initialSelectedIds 初始选中的项目ID列表（开始服务时选择的项目）
     */
    fun initData(orderInfoRequest: OrderInfoRequestModel, initialSelectedIds: List<Int>) {
        viewModelScope.launch {
            // 尝试获取缓存的订单详情
            val cachedOrderInfo = sharedOrderRepository.getCachedOrderInfo(orderInfoRequest)
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
                when (val result = sharedOrderRepository.getOrderInfo(orderInfoRequest)) {
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
                        _uiState.value = EndServiceSelectionUiState.Error(result.exception.message ?: "未知错误")
                    }
                }
            }
        }
    }

    /**
     * 设置选中状态
     */
    fun setSelection(projectIds: Set<Int>) {
        _selectedProjectIds.value = projectIds
    }

    /**
     * 全选
     */
    fun selectAll() {
        _selectedProjectIds.value = _projectList.value.map { it.projectId }.toSet()
    }
    
    /**
     * 取消全选
     */
    fun deselectAll() {
        _selectedProjectIds.value = emptySet()
    }

    /**
     * 切换项目的选中状态
     * @param projectId 项目ID
     */
    fun toggleSelection(projectId: Int) {
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
}

sealed class EndServiceSelectionUiState {
    data object Loading : EndServiceSelectionUiState()
    data object Success : EndServiceSelectionUiState()
    data class Error(val message: String) : EndServiceSelectionUiState()
}
