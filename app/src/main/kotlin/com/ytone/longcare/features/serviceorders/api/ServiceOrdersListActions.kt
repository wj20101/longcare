package com.ytone.longcare.features.serviceorders.api

import com.ytone.longcare.navigation.OrderNavParams

data class ServiceOrdersListActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToNursingExecution: (OrderNavParams) -> Unit,
    val onNavigateToService: (OrderNavParams) -> Unit
)
