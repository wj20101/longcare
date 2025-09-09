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
     * 经度
     */
    @param:Json(name = "longitude")
    val longitude: String = "",
    
    /**
     * 纬度
     */
    @param:Json(name = "latitude")
    val latitude: String = "",
    
    /**
     * 选中的服务项目ID集合
     */
    @param:Json(name = "porjectIdList")
    val porjectIdList: List<Int> = emptyList()
)