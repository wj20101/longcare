package com.ytone.longcare.features.maindashboard.api

import com.ytone.longcare.model.OrderKey

internal data class MainDashboardActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit
)
