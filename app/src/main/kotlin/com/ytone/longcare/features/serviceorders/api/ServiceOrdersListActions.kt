package com.ytone.longcare.features.serviceorders.api

import com.ytone.longcare.model.OrderKey

data class ServiceOrdersListActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit
)
