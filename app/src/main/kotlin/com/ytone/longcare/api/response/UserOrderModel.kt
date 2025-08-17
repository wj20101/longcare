package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 用户服务记录模型
 */
@JsonClass(generateAdapter = true)
data class UserOrderModel(
    /**
     * 订单号
     */
    @param:Json(name = "ordreId")
    val ordreId: Long = 0L,

    /**
     * 服务开始时间
     */
    @param:Json(name = "startTime")
    val startTime: String = "",

    /**
     * 服务结束时间
     */
    @param:Json(name = "endTime")
    val endTime: String = "",

    /**
     * 总的服务时长
     */
    @param:Json(name = "totalServiceTime")
    val totalServiceTime: Int = 0
)