package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 文件上传token
 */
@JsonClass(generateAdapter = true)
data class UploadTokenResultModel(
    @param:Json(name = "tmpSecretId")
    val tmpSecretId: String = "",
    @param:Json(name = "tmpSecretKey")
    val tmpSecretKey: String = "",
    @param:Json(name = "sessionToken")
    val sessionToken: String = "",
    @param:Json(name = "startTime")
    val startTime: String = "",
    @param:Json(name = "requestId")
    val requestId: String = "",
    @param:Json(name = "expiration")
    val expiration: String = "",
    @param:Json(name = "expiredTime")
    val expiredTime: String = "",
    @param:Json(name = "bucket")
    val bucket: String = "",
    @param:Json(name = "region")
    val region: String = ""
)