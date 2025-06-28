package com.ytone.longcare.api.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 获取文件上传token请求参数
 */
@JsonClass(generateAdapter = true)
data class UploadTokenParamModel(
    /**
     * 文件夹类型 13 服务图片
     */
    @param:Json(name = "folderType")
    val folderType: Int? = null
)