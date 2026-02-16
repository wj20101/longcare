package com.ytone.longcare.features.selectdevice.api

import com.ytone.longcare.navigation.OrderNavParams

data class SelectDeviceActions(
    val onNavigateBack: () -> Unit,
    val onStartOrderNfcSignIn: (OrderNavParams) -> Unit
)
