package com.ytone.longcare.api.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 文件上传token
 */
@JsonClass(generateAdapter = true)
data class UploadTokenResultModel(
    @field:Json("tmpSecretId")
    val tmpSecretId: String = "",
    @field:Json("tmpSecretKey")
    val tmpSecretKey: String = "",
    @field:Json("sessionToken")
    val sessionToken: String = "",
    @field:Json("startTime")
    val startTime: String = "",
    @field:Json("requestId")
    val requestId: String = "",
    @field:Json("expiration")
    val expiration: String = "",
    @field:Json("expiredTime")
    val expiredTime: String = "",
    @field:Json("bucket")
    val bucket: String = "",
    @field:Json("region")
    val region: String = ""
)