package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 添加开始老人照片请求参数模型
 */
@JsonClass(generateAdapter = true)
data class UpUserStartImgParamModel(
    @param:Json(name = "orderid")
    val orderId: Long,

    @param:Json(name = "userImgList")
    val userImgList: List<String>
)