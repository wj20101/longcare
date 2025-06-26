package com.ytone.longcare.data.cos.model

import kotlinx.serialization.Serializable

/**
 * COS上传结果
 */
@Serializable
data class CosUploadResult(
    val success: Boolean,
    val url: String? = null,
    val key: String? = null,
    val bucket: String? = null,
    val region: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null
)

/**
 * COS配置信息
 */
data class CosConfig(
    val region: String,
    val bucket: String,
    val secretId: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null,
    val expiredTime: Long? = null
)

/**
 * COS临时密钥信息
 */
@Serializable
data class CosCredentials(
    val tmpSecretId: String,
    val tmpSecretKey: String,
    val sessionToken: String,
    val expiredTime: Long,
    val startTime: Long
)

/**
 * 上传进度回调
 */
data class UploadProgress(
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
}

/**
 * 上传参数
 */
data class UploadParams(
    val filePath: String,
    val key: String,
    val contentType: String? = null,
    val metadata: Map<String, String>? = null
)