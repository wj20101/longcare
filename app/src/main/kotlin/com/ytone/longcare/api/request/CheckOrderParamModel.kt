package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 工单前校验请求参数模型
 */
@JsonClass(generateAdapter = true)
data class CheckOrderParamModel(
    @param:Json(name = "orderid")
    val orderId: Long,

    @param:Json(name = "nfc")
    val nfc: String,

    @param:Json(name = "longitude")
    val longitude: String,

    @param:Json(name = "latitude")
    val latitude: String
)