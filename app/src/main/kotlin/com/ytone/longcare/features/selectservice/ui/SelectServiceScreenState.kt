package com.ytone.longcare.features.selectservice.ui

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

internal fun loadOrderInfoIfNeeded(sharedViewModel: SharedOrderDetailViewModel, orderKey: OrderKey) {
    if (sharedViewModel.getCachedOrderInfo(orderKey) == null) {
        sharedViewModel.getOrderInfo(orderKey)
    } else {
        sharedViewModel.getOrderInfo(orderKey, forceRefresh = false)
    }
}

internal fun updateServiceItemsFromUiState(
    uiState: OrderDetailUiState,
    selectServiceType: Int,
    serviceItems: SnapshotStateList<ServiceItem>
) {
    when (val currentState = uiState) {
        is OrderDetailUiState.Success -> {
            serviceItems.clear()
            serviceItems.addAll(
                (currentState.orderInfo.projectList ?: emptyList()).map { project ->
                    ServiceItem(
                        id = project.projectId,
                        name = project.projectName,
                        duration = project.serviceTime,
                        isSelected = selectServiceType != 0
                    )
                }
            )
        }

        else -> {
            serviceItems.clear()
        }
    }
}

internal fun selectedProjectIds(serviceItems: List<ServiceItem>): List<Int> {
    return serviceItems.filter { it.isSelected }.map { it.id }
}
