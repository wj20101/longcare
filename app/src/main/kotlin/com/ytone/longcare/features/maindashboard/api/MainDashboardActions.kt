package com.ytone.longcare.features.maindashboard.api

import com.ytone.longcare.navigation.OrderNavParams

data class MainDashboardActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderNavParams) -> Unit,
    val onNavigateToService: (OrderNavParams) -> Unit,
    val onNavigateToServiceCountdown: (OrderNavParams, List<Int>) -> Unit
)
