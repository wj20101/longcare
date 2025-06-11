package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 获取本月用户的服务记录情况 请求参数
 */
@Serializable
data class UserOrderParamModel(
    /**
     * 老人用户Id
     */
    @SerialName("userId")
    val userId: Int = 0
)