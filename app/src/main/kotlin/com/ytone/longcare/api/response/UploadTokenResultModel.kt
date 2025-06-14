package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 文件上传token
 */
@JsonClass(generateAdapter = true)
data class UploadTokenResultModel(
    @Json(name = "tmpSecretId")
    val tmpSecretId: String = "",
    @Json(name = "tmpSecretKey")
    val tmpSecretKey: String = "",
    @Json(name = "sessionToken")
    val sessionToken: String = "",
    @Json(name = "startTime")
    val startTime: String = "",
    @Json(name = "requestId")
    val requestId: String = "",
    @Json(name = "expiration")
    val expiration: String = "",
    @Json(name = "expiredTime")
    val expiredTime: String = "",
    @Json(name = "bucket")
    val bucket: String = "",
    @Json(name = "region")
    val region: String = ""
)