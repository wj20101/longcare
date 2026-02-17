package com.ytone.longcare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ytone.longcare.model.OrderInfoRequestModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toRequestModel

@Composable
fun rememberOrderInfoRequest(orderKey: OrderKey): OrderInfoRequestModel =
    remember(orderKey) { orderKey.toRequestModel() }
