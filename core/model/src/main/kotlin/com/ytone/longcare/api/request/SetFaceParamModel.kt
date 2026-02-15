package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 设置人脸信息参数
 */
@JsonClass(generateAdapter = true)
data class SetFaceParamModel(
    /**
     * base64格式的图片 图片不能超过500k，且必须为 JPG 或 PNG、BMP 格式
     */
    @param:Json(name = "faceImg")
    val faceImg: String = "",

    /**
     * 人脸图片地址
     */
    @param:Json(name = "faceImgUrl")
    val faceImgUrl: String = ""
)