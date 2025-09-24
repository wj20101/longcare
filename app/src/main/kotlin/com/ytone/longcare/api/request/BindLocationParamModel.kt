package com.ytone.longcare.api.request

import com.squareup.moshi.Json

data class BindLocationParamModel(
    @param:Json(name = "orderid")
    val orderId: Long = 0,
    @param:Json(name = "nfc")
    val nfc: String? = null,
    @param:Json(name = "longitude")
    val longitude: String? = null,
    @param:Json(name = "latitude")
    val latitude: String? = null
)
