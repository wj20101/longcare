package com.ytone.longcare.features.nfc.api

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.ServiceCompleteData

data class NfcWorkflowActions(
    val onNavigateBack: () -> Unit,
    val onNavigateHomeAndClearStack: () -> Unit,
    val onNavigateToIdentification: (OrderKey) -> Unit,
    val onNavigateToServiceComplete: (OrderKey, ServiceCompleteData) -> Unit
)
