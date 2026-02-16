package com.ytone.longcare.features.home.api

import com.ytone.longcare.navigation.OrderNavParams

data class HomeActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderNavParams) -> Unit,
    val onNavigateToService: (OrderNavParams) -> Unit,
    val onNavigateToServiceCountdown: (OrderNavParams, List<Int>) -> Unit,
    val onNavigateToHaveServiceUserList: () -> Unit,
    val onNavigateToNoServiceUserList: () -> Unit
)
