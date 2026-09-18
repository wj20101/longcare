package com.ytone.longcare.features.nursingexecution.api

import com.ytone.longcare.model.OrderKey

data class NursingExecutionActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit,
    val onStartOrderNfcSignIn: (OrderKey) -> Unit
)
