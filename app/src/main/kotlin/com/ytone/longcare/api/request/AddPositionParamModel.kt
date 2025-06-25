package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 定位参数信息
 */
@JsonClass(generateAdapter = true)
data class AddPositionParamModel(
    /**
     * 订单号
     */
    @param:Json(name = "orderid")
    val orderId: Long = 0L,

    /**
     * 经度
     */
    @param:Json(name = "longitude")
    val longitude: Double = 0.0,

    /**
     * 纬度
     */
    @param:Json(name = "latitude")
    val latitude: Double = 0.0
)