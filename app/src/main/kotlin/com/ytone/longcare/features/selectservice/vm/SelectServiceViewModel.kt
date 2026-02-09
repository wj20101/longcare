package com.ytone.longcare.features.selectservice.vm

import androidx.lifecycle.ViewModel
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.toOrderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SelectServiceViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val unifiedOrderRepository: UnifiedOrderRepository
) : ViewModel() {

    suspend fun getSelectServiceType(): Int {
        return systemConfigManager.getSelectServiceType()
    }

    suspend fun updateSelectedProjects(
        orderInfoRequest: OrderInfoRequestModel,
        selectedProjectIds: List<Int>
    ) {
        unifiedOrderRepository.updateSelectedProjects(orderInfoRequest.toOrderKey(), selectedProjectIds)
    }
}
