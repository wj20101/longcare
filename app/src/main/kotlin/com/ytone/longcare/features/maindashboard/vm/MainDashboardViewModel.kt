package com.ytone.longcare.features.maindashboard.vm

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.core.ui.R as CoreUiR
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceCountdownNavigationData(
    val orderKey: OrderKey,
    val projectIdList: List<Int>
)

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val unifiedOrderRepository: OrderDetailRepository,
) : ViewModel() {
    private val _companyName = MutableStateFlow("")
    val companyName: StateFlow<String> = _companyName.asStateFlow()
    private var loadCompanyNameJob: Job? = null
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    fun loadCompanyName() {
        if (loadCompanyNameJob?.isActive == true) return
        loadCompanyNameJob = viewModelScope.launch {
            val refreshedCompanyName = systemConfigManager.refreshCompanyName()?.trim().orEmpty()
            if (refreshedCompanyName.isNotEmpty()) {
                _companyName.value = refreshedCompanyName
                return@launch
            }

            if (_companyName.value.isEmpty()) {
                _companyName.value = systemConfigManager.getCompanyName()
            }
        }
    }

    suspend fun buildServiceCountdownNavigationData(
        orderId: Long,
        planId: Int = 0
    ): ServiceCountdownNavigationData? {
        val orderKey = OrderKey(orderId = orderId, planId = planId)

        val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey) ?: when (
            val result = unifiedOrderRepository.getOrderInfo(orderKey)
        ) {
            is ApiResult.Success -> result.data
            is ApiResult.Exception -> {
                messageQueue.enqueue(CoreUiR.string.common_network_error_retry)
                logE("获取订单详情异常: orderId=$orderId", throwable = result.exception)
                return null
            }
            is ApiResult.Failure -> {
                messageQueue.enqueue(result.message)
                logE("获取订单详情失败: orderId=$orderId, message=${result.message}")
                return null
            }
        }

        val projectList = orderInfo.projectList ?: emptyList()
        val savedProjectIds = unifiedOrderRepository.getSelectedProjectIds(orderKey)
        val selectedProjectIds = if (savedProjectIds.isEmpty()) {
            projectList.map { it.projectId }
        } else {
            savedProjectIds
        }

        return ServiceCountdownNavigationData(
            orderKey = orderKey,
            projectIdList = selectedProjectIds
        )
    }
}
