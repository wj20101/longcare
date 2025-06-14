package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 按天查询服务订单的请求参数模型
 *
 * @property daytime 查询日期，格式例如: "yyyy-MM-dd"
 */
@JsonClass(generateAdapter = true)
data class OrderListParamModel(
    @Json(name = "daytime") val daytime: String = ""
)