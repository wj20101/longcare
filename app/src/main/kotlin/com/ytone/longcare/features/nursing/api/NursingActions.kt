package com.ytone.longcare.features.nursing.api

import com.ytone.longcare.model.OrderKey

data class NursingActions(
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit
)
