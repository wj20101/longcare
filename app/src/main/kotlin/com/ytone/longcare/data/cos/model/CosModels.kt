package com.ytone.longcare.data.cos.model

import kotlinx.serialization.Serializable
import com.ytone.longcare.api.response.UploadTokenResultModel

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
 * 整合了临时密钥和配置信息，用于统一管理COS相关参数
 */
data class CosConfig(
    val region: String,
    val bucket: String,
    val secretId: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null,
    val expiredTime: Long? = null,
    // 从UploadTokenResultModel扩展的字段
    val tmpSecretId: String? = null,
    val tmpSecretKey: String? = null,
    val startTime: String? = null,
    val requestId: String? = null,
    val expiration: String? = null,
    val expiredTimeStr: String? = null
) {
    /**
     * 判断是否为临时密钥配置
     */
    val isTemporaryCredentials: Boolean
        get() = !tmpSecretId.isNullOrEmpty() && !tmpSecretKey.isNullOrEmpty() && !sessionToken.isNullOrEmpty()
    
    /**
     * 判断是否为固定密钥配置
     */
    val isStaticCredentials: Boolean
        get() = !secretId.isNullOrEmpty() && !secretKey.isNullOrEmpty()
    
    /**
     * 获取有效的过期时间（优先使用Long类型的expiredTime）
     */
    val effectiveExpiredTime: Long
        get() = expiredTime ?: expiredTimeStr?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3600)
    
    /**
     * 检查密钥是否即将过期（提前5分钟）
     */
    fun isExpiringSoon(thresholdSeconds: Long = 300): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= (effectiveExpiredTime - thresholdSeconds)
    }
}

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

/**
 * UploadTokenResultModel 转换为 CosConfig 的扩展函数
 */
fun UploadTokenResultModel.toCosConfig(): CosConfig {
    return CosConfig(
        region = this.region,
        bucket = this.bucket,
        sessionToken = this.sessionToken,
        tmpSecretId = this.tmpSecretId,
        tmpSecretKey = this.tmpSecretKey,
        startTime = this.startTime,
        requestId = this.requestId,
        expiration = this.expiration,
        expiredTimeStr = this.expiredTime,
        // 将字符串时间转换为Long类型的时间戳
        expiredTime = this.expiredTime.toLongOrNull()
    )
}

/**
 * 从临时密钥信息创建 CosConfig
 */
fun CosCredentials.toCosConfig(region: String, bucket: String): CosConfig {
    return CosConfig(
        region = region,
        bucket = bucket,
        sessionToken = this.sessionToken,
        tmpSecretId = this.tmpSecretId,
        tmpSecretKey = this.tmpSecretKey,
        expiredTime = this.expiredTime
    )
}