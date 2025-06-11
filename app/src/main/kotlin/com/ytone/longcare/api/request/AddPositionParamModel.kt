package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 定位参数信息
 */
@Serializable
data class AddPositionParamModel(
    /**
     * 订单号
     */
    @SerialName("orderid")
    val orderId: Long = 0L,

    /**
     * 经度
     */
    @SerialName("longitude")
    val longitude: Double = 0.0,

    /**
     * 纬度
     */
    @SerialName("latitude")
    val latitude: Double = 0.0
)