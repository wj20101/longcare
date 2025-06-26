package com.ytone.longcare.data.cos.config

import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COS配置管理器
 * 负责管理COS相关的配置信息
 */
@Singleton
class CosConfigManager @Inject constructor() {
    
    companion object {
        // 默认配置常量
        private const val DEFAULT_REGION = "ap-beijing"
        private const val DEFAULT_BUCKET = "your-bucket-name"
        
        // 临时密钥有效期（秒）
        private const val CREDENTIALS_EXPIRE_BUFFER = 300 // 5分钟缓冲时间
    }
    
    private var currentCredentials: CosCredentials? = null
    private var currentRegion: String = DEFAULT_REGION
    private var currentBucket: String = DEFAULT_BUCKET
    
    /**
     * 设置COS基本配置
     * @param region 地域
     * @param bucket 存储桶名称
     */
    fun setBasicConfig(region: String, bucket: String) {
        currentRegion = region
        currentBucket = bucket
    }
    
    /**
     * 设置临时密钥
     * @param credentials 临时密钥信息
     */
    fun setCredentials(credentials: CosCredentials) {
        currentCredentials = credentials
    }
    
    /**
     * 获取当前配置
     * @return COS配置信息，如果没有临时密钥返回null
     */
    fun getCurrentConfig(): CosConfig? {
        val credentials = currentCredentials ?: return null
        
        return CosConfig(
            region = currentRegion,
            bucket = currentBucket,
            secretId = credentials.tmpSecretId,
            secretKey = credentials.tmpSecretKey,
            sessionToken = credentials.sessionToken,
            expiredTime = credentials.expiredTime
        )
    }
    
    /**
     * 获取当前临时密钥
     * @return 临时密钥信息
     */
    fun getCurrentCredentials(): CosCredentials? {
        return currentCredentials
    }
    
    /**
     * 获取当前地域
     * @return 地域
     */
    fun getCurrentRegion(): String {
        return currentRegion
    }
    
    /**
     * 获取当前存储桶
     * @return 存储桶名称
     */
    fun getCurrentBucket(): String {
        return currentBucket
    }
    
    /**
     * 检查临时密钥是否即将过期
     * @param bufferTimeInSeconds 缓冲时间（秒），默认5分钟
     * @return 是否即将过期
     */
    fun isCredentialsExpiringSoon(bufferTimeInSeconds: Long = CREDENTIALS_EXPIRE_BUFFER.toLong()): Boolean {
        val credentials = currentCredentials ?: return true
        val currentTime = System.currentTimeMillis() / 1000
        return (credentials.expiredTime - currentTime) <= bufferTimeInSeconds
    }
    
    /**
     * 检查临时密钥是否已过期
     * @return 是否已过期
     */
    fun isCredentialsExpired(): Boolean {
        val credentials = currentCredentials ?: return true
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= credentials.expiredTime
    }
    
    /**
     * 清除当前配置
     */
    fun clearConfig() {
        currentCredentials = null
    }
    
    /**
     * 获取临时密钥剩余有效时间（秒）
     * @return 剩余有效时间，如果已过期或无密钥返回0
     */
    fun getRemainingValidTime(): Long {
        val credentials = currentCredentials ?: return 0
        val currentTime = System.currentTimeMillis() / 1000
        val remaining = credentials.expiredTime - currentTime
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * 创建默认的上传路径前缀
     * @param userId 用户ID
     * @param category 分类（如：avatar, document, image等）
     * @return 路径前缀
     */
    fun createUploadPrefix(userId: String, category: String): String {
        return "users/$userId/$category"
    }
    
    /**
     * 验证配置是否完整
     * @return 配置是否完整且有效
     */
    fun isConfigValid(): Boolean {
        return currentCredentials != null && 
               currentRegion.isNotEmpty() && 
               currentBucket.isNotEmpty() && 
               !isCredentialsExpired()
    }
    
    /**
     * 获取配置状态描述
     * @return 配置状态描述
     */
    fun getConfigStatus(): String {
        return when {
            currentCredentials == null -> "未配置临时密钥"
            isCredentialsExpired() -> "临时密钥已过期"
            isCredentialsExpiringSoon() -> "临时密钥即将过期（${getRemainingValidTime()}秒后）"
            else -> "配置正常（${getRemainingValidTime()}秒后过期）"
        }
    }
}