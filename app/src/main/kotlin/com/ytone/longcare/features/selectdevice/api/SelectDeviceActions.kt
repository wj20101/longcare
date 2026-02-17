package com.ytone.longcare.features.selectdevice.api

import com.ytone.longcare.model.OrderKey

data class SelectDeviceActions(
    val onNavigateBack: () -> Unit,
    val onStartOrderNfcSignIn: (OrderKey) -> Unit
)
