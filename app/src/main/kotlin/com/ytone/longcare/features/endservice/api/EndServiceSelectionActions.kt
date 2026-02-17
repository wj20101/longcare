package com.ytone.longcare.features.endservice.api

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo

data class EndServiceSelectionActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToNfcSignInForEndOrder: (OrderKey, EndOderInfo) -> Unit
)
