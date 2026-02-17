package com.ytone.longcare.navigation

import com.ytone.longcare.model.OrderKey

fun OrderNavParams.toOrderKey() = OrderKey(orderId, planId)

@Deprecated(
    message = "Use toOrderKey() instead",
    replaceWith = ReplaceWith("toOrderKey()")
)
fun OrderNavParams.toOrderKeyModel() = toOrderKey()

fun OrderKey.toOrderNavParams() = OrderNavParams(orderId, planId)
