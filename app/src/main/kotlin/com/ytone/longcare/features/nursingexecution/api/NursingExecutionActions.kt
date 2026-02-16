package com.ytone.longcare.features.nursingexecution.api

import com.ytone.longcare.navigation.OrderNavParams

data class NursingExecutionActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToServiceCountdown: (OrderNavParams, List<Int>) -> Unit,
    val onNavigateToSelectDevice: (OrderNavParams) -> Unit
)
