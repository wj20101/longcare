package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 结束服务工单响应模型
 */
@JsonClass(generateAdapter = true)
data class EndOrderResultModel(
    /**
     * 实际服务时长（分钟）
     */
    @param:Json(name = "trueServiceTime")
    val trueServiceTime: Int = 0
)
