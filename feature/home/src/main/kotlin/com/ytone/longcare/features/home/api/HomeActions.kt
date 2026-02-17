package com.ytone.longcare.features.home.api

import com.ytone.longcare.model.OrderKey

data class HomeActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit,
    val onNavigateToHaveServiceUserList: () -> Unit,
    val onNavigateToNoServiceUserList: () -> Unit
)
