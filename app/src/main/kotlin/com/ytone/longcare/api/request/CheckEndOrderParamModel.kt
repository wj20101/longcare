package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 检测结束工单参数
 */
@JsonClass(generateAdapter = true)
data class CheckEndOrderParamModel(
    /**
     * 服务工单
     */
    @param:Json(name = "orderid")
    val orderid: Long = 0L,

    /**
     * 完成的服务项目Id集合
     */
    @param:Json(name = "porjectIdList")
    val porjectIdList: List<Int> = emptyList()
)