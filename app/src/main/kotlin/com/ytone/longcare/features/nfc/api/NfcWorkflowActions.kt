package com.ytone.longcare.features.nfc.api

import com.ytone.longcare.navigation.OrderNavParams
import com.ytone.longcare.navigation.ServiceCompleteData

data class NfcWorkflowActions(
    val onNavigateBack: () -> Unit,
    val onNavigateHomeAndClearStack: () -> Unit,
    val onNavigateToIdentification: (OrderNavParams) -> Unit,
    val onNavigateToServiceComplete: (OrderNavParams, ServiceCompleteData) -> Unit
)
