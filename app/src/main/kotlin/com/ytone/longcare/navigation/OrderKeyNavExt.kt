package com.ytone.longcare.navigation

import com.ytone.longcare.model.OrderKey

fun OrderNavParams.toOrderKey() = OrderKey(orderId, planId)

fun OrderKey.toOrderNavParams() = OrderNavParams(orderId, planId)
