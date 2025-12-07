package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 查询服务订单状态参数
 */
@JsonClass(generateAdapter = true)
data class OrderStateParamModel(
    /**
     * 查询的订单号
     */
    @param:Json(name = "orderid")
    val orderId: Long = 0L
)
