package com.ytone.longcare.features.endservice.api

import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.OrderNavParams

data class EndServiceSelectionActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToNfcSignInForEndOrder: (OrderNavParams, EndOderInfo) -> Unit
)
