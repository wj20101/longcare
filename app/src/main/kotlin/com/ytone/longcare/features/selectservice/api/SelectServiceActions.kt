package com.ytone.longcare.features.selectservice.api

import com.ytone.longcare.navigation.OrderNavParams

data class SelectServiceActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToServiceCountdown: (OrderNavParams, List<Int>) -> Unit
)
