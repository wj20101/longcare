package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 获取本月用户的服务记录情况 请求参数
 */
@JsonClass(generateAdapter = true)
data class UserOrderParamModel(
    /**
     * 老人用户Id
     */
    @Json(name = "userId")
    val userId: Int = 0
)