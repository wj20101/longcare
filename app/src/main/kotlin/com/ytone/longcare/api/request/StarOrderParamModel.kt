package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 工单开始(正式计时)请求参数模型
 */
@JsonClass(generateAdapter = true)
data class StarOrderParamModel(
    @param:Json(name = "orderid")
    val orderId: Long,
    
    /**
     * 选中的项目ID列表
     */
    @param:Json(name = "selectedProjectIds")
    val selectedProjectIds: List<Long> = emptyList()
)