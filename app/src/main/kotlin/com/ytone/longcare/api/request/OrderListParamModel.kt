package com.ytone.longcare.api.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 按天查询服务订单的请求参数模型
 *
 * @property daytime 查询日期，格式例如: "yyyy-MM-dd"
 */
@Serializable
data class OrderListParamModel(
    @SerialName("daytime") val daytime: String = ""
)