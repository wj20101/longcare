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
    @Json(name = "orderid")
    val orderId: Long = 0L,

    /**
     * nfc设备号
     */
    @Json(name = "nfc")
    val nfc: String = ""
)