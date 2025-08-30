package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 查询服务订单详情参数
 */
@JsonClass(generateAdapter = true)
data class OrderInfoParamModel(
    /**
     * 查询的订单号
     */
    @param:Json(name = "orderid")
    val orderId: Long = 0L,
    /**
     * 计划Id
     */
    @param:Json(name = "planid")
    val planId: Int = 0
)