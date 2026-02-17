package com.ytone.longcare.navigation

import com.ytone.longcare.model.OrderKey

fun OrderNavParams.toOrderKeyModel() = OrderKey(orderId, planId)

fun OrderKey.toOrderNavParams() = OrderNavParams(orderId, planId)
