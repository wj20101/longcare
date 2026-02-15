package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 获取人脸信息响应模型
 */
@JsonClass(generateAdapter = true)
data class FaceResultModel(
    /** 人脸照片地址 */
    @param:Json(name = "faceImgUrl")
    val faceImgUrl: String = ""
)