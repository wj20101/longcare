package com.ytone.longcare.features.nursing.api

import com.ytone.longcare.navigation.OrderNavParams

data class NursingActions(
    val onNavigateToNursingExecution: (OrderNavParams) -> Unit,
    val onNavigateToService: (OrderNavParams) -> Unit
)
