package com.ytone.longcare.features.selectservice.api

import com.ytone.longcare.model.OrderKey

data class SelectServiceActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit
)
