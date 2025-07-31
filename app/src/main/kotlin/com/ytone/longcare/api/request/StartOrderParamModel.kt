package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 开始服务工单参数
 */
@JsonClass(generateAdapter = true)
data class StartOrderParamModel(
    /**
     * 订单Id
     */
    @param:Json(name = "orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @param:Json(name = "nfc")
    val nfc: String = "",

    /**
     * 经度
     */
    @param:Json(name = "longitude")
    val longitude: String = "",

    /**
     * 纬度
     */
    @param:Json(name = "latitude")
    val latitude: String = ""
)