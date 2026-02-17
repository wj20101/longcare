package com.ytone.longcare.features.selectservice.vm

import androidx.lifecycle.ViewModel
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SelectServiceViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val unifiedOrderRepository: OrderDetailRepository
) : ViewModel() {

    suspend fun getSelectServiceType(): Int {
        return systemConfigManager.getSelectServiceType()
    }

    suspend fun updateSelectedProjects(
        orderKey: OrderKey,
        selectedProjectIds: List<Int>
    ) {
        unifiedOrderRepository.updateSelectedProjects(orderKey, selectedProjectIds)
    }
}
