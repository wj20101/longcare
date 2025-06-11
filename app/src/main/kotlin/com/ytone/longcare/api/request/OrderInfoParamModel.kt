package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 查询服务订单详情参数
 */
@Serializable
data class OrderInfoParamModel(
    /**
     * 查询的订单号
     */
    @SerialName("orderid")
    val orderId: Long = 0L
)