package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BindLocationParamModel(
    @param:Json(name = "orderid")
    val orderId: Long = 0,
    @param:Json(name = "nfc")
    val nfc: String = "",
    @param:Json(name = "longitude")
    val longitude: String = "",
    @param:Json(name = "latitude")
    val latitude: String = ""
)
