package com.ytone.longcare.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 文件上传token
 */
@Serializable
data class UploadTokenResultModel(
    @SerialName("tmpSecretId")
    val tmpSecretId: String = "",
    @SerialName("tmpSecretKey")
    val tmpSecretKey: String = "",
    @SerialName("sessionToken")
    val sessionToken: String = "",
    @SerialName("startTime")
    val startTime: String = "",
    @SerialName("requestId")
    val requestId: String = "",
    @SerialName("expiration")
    val expiration: String = "",
    @SerialName("expiredTime")
    val expiredTime: String = "",
    @SerialName("bucket")
    val bucket: String = "",
    @SerialName("region")
    val region: String = ""
)