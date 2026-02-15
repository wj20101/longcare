package com.ytone.longcare.model

import com.ytone.longcare.navigation.OrderNavParams

fun OrderNavParams.toOrderKey() = OrderKey(orderId, planId)

fun OrderKey.toNavParams() = OrderNavParams(orderId, planId)
