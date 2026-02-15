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
    val expiration: String,
    val fileKeyPre: String
) {

    /**
     * 检查密钥是否即将过期（提前5分钟）
     */
    fun isExpiringSoon(thresholdSeconds: Long = 300): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= (expiredTime - thresholdSeconds)
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
    val folderType: Int,
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
        expiration = this.expiration,
        expiredTime = this.expiredTime.toLongOrNull() ?: 0L,
        fileKeyPre = this.fileKeyPre
    )
}