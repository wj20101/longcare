package com.ytone.longcare.features.maindashboard.vm

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.navigation.OrderNavParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceCountdownNavigationData(
    val orderParams: OrderNavParams,
    val projectIdList: List<Int>
)

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {
    private val _companyName = MutableStateFlow("")
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    fun loadCompanyName() {
        if (_companyName.value.isNotEmpty()) return
        viewModelScope.launch {
            _companyName.value = systemConfigManager.getCompanyName()
        }
    }

    suspend fun buildServiceCountdownNavigationData(
        orderId: Long,
        planId: Int = 0
    ): ServiceCountdownNavigationData? {
        val request = OrderInfoRequestModel(orderId = orderId, planId = planId)
        val orderKey = request.toOrderKey()

        val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey) ?: when (
            val result = unifiedOrderRepository.getOrderInfo(orderKey)
        ) {
            is ApiResult.Success -> result.data
            is ApiResult.Exception -> {
                val message = result.exception.message ?: "网络错误，请检查网络连接"
                toastHelper.showShort(message)
                logE("获取订单详情异常: orderId=$orderId, message=$message", throwable = result.exception)
                return null
            }
            is ApiResult.Failure -> {
                toastHelper.showShort(result.message)
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
            orderParams = OrderNavParams(orderId = orderId, planId = planId),
            projectIdList = selectedProjectIds
        )
    }
}
