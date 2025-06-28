package com.ytone.longcare.data.cos.model

import android.net.Uri
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
    val sessionToken: String,
    val expiredTime: Long,
    val tmpSecretId: String,
    val tmpSecretKey: String,
    val startTime: Long,
    val requestId: String,
    val expiration: String,
    val expiredTimeStr: String,
    val fileKeyPre: String
) {
    /**
     * 判断是否为临时密钥配置
     */
    val isTemporaryCredentials: Boolean
        get() = tmpSecretId.isNotEmpty() && tmpSecretKey.isNotEmpty() && sessionToken.isNotEmpty()

    /**
     * 判断是否为固定密钥配置
     */
    val isStaticCredentials: Boolean
        get() = tmpSecretId.isNotEmpty() && tmpSecretKey.isNotEmpty()


    /**
     * 获取有效的过期时间（优先使用Long类型的expiredTime）
     */
    val effectiveExpiredTime: Long
        get() = expiredTime

    /**
     * 检查密钥是否即将过期（提前5分钟）
     */
    fun isExpiringSoon(thresholdSeconds: Long = 300): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= (effectiveExpiredTime - thresholdSeconds)
    }
}

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
    val fileUri: Uri,
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
        startTime = this.startTime.toLongOrNull() ?: 0L,
        requestId = this.requestId,
        expiration = this.expiration,
        expiredTimeStr = this.expiredTime,
        expiredTime = this.expiredTime.toLongOrNull() ?: 0L,
        fileKeyPre = this.fileKeyPre
    )
}