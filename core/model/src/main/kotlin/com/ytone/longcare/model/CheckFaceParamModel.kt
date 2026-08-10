package com.ytone.longcare.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 人脸对比验证参数。
 *
 * @property orderId 订单 ID，接口文档定义为 int32
 * @property faceImg Base64 图片，不能超过 500 KB，格式须为 JPG、PNG 或 BMP
 */
@JsonClass(generateAdapter = true)
data class CheckFaceParamModel(
    @param:Json(name = "orderId")
    val orderId: Int,

    @param:Json(name = "faceImg")
    val faceImg: String?,
)
