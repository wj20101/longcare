package com.ytone.longcare.data.cos.repository

import android.content.Context
import android.util.Log
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.CosXmlServiceConfig
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.model.`object`.DeleteObjectRequest
import com.tencent.cos.xml.model.`object`.HeadObjectRequest
import com.tencent.cos.xml.model.`object`.PutObjectRequest
import com.tencent.qcloud.core.auth.QCloudCredentialProvider
import com.tencent.qcloud.core.auth.QCloudLifecycleCredentials
import com.tencent.qcloud.core.auth.SessionQCloudCredentials
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.UploadTokenParamModel
import com.ytone.longcare.api.request.SaveFileParamModel
import com.ytone.longcare.api.response.UploadTokenResultModel
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.getFileSize
import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import com.ytone.longcare.data.cos.model.toCosConfig
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 腾讯云COS存储服务实现类
 *
 * 主要功能：
 * - 支持临时密钥和固定密钥两种认证方式
 * - 自动token刷新和缓存机制
 * - 文件上传（支持进度回调）
 * - 文件删除、存在性检查、大小获取
 * - 下载URL生成
 *
 * 线程安全：使用Mutex保证并发安全
 * 错误处理：统一的重试机制和错误处理
 */
@Singleton
class CosRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: LongCareApiService
) : CosRepository {

    companion object {
        private const val TAG = "CosRepositoryImpl"
        private const val TOKEN_REFRESH_THRESHOLD_SECONDS = 300L // 5分钟提前刷新
        private const val DEFAULT_FOLDER_TYPE = 13 // 默认文件夹类型
    }

    // 线程安全的状态管理 - 分离锁职责避免死锁
    private val serviceMutex = Mutex()  // 专门保护服务初始化
    private val configMutex = Mutex()   // 专门保护配置刷新
    private val serviceRef = AtomicReference<CosXmlService?>(null)
    private val configCache = CosConfigCache()

    /**
     * CosConfig缓存管理类
     * 统一管理COS配置信息，包括临时密钥、bucket、region等
     */
    private class CosConfigCache {
        @Volatile
        var cosConfig: CosConfig? = null

        fun isValid(): Boolean {
            val config = cosConfig ?: return false
            return !config.isExpiringSoon(TOKEN_REFRESH_THRESHOLD_SECONDS)
        }

        fun clear() {
            cosConfig = null
        }

        fun update(config: CosConfig) {
            cosConfig = config
        }

        fun updateFromToken(token: UploadTokenResultModel) {
            cosConfig = token.toCosConfig()
        }
    }

    /**
     * 动态密钥提供者 - 支持自动刷新临时密钥
     */
    private inner class DynamicCredentialProvider : QCloudCredentialProvider {

        override fun getCredentials(): QCloudLifecycleCredentials? {
            return try {
                // 检查并刷新配置
                if (!configCache.isValid()) {
                    refreshConfigSync()
                }

                configCache.cosConfig?.let { config ->
                    SessionQCloudCredentials(
                        config.tmpSecretId,
                        config.tmpSecretKey,
                        config.sessionToken,
                        config.startTime,
                        config.expiredTime
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get credentials", e)
                null
            }
        }

        override fun refresh() {
            try {
                refreshConfigSync()
                Log.d(TAG, "Credentials refreshed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh credentials", e)
            }
        }

        /**
         * 同步刷新配置（在密钥提供者回调中使用）
         */
        private fun refreshConfigSync() {
            runBlocking {
                refreshCosConfig()
            }
        }
    }

    /**
     * 获取有效的COS服务实例
     */
    private suspend fun getCosService(): CosXmlService {
        serviceRef.get()?.let { return it }

        return serviceMutex.withLock {
            // 双重检查
            serviceRef.get()?.let { return@withLock it }

            // 获取配置并初始化服务
            val config = getValidCosConfig()
            val service = createCosService(config)
            serviceRef.set(service)

            Log.d(
                TAG,
                "COS service initialized with bucket: ${config.bucket}, region: ${config.region}"
            )
            service
        }
    }

    /**
     * 创建COS服务实例
     */
    private fun createCosService(config: CosConfig): CosXmlService {
        val credentialProvider = DynamicCredentialProvider()
        val serviceConfig = CosXmlServiceConfig.Builder()
            .setRegion(config.region)
            .isHttps(true)
            .builder()

        return CosXmlService(context, serviceConfig, credentialProvider)
    }

    /**
     * 获取有效的COS配置
     */
    private suspend fun getValidCosConfig(folderType: Int = DEFAULT_FOLDER_TYPE): CosConfig {
        if (configCache.isValid()) {
            return configCache.cosConfig!!
        }

        return configMutex.withLock {
            // 双重检查
            if (configCache.isValid()) {
                return@withLock configCache.cosConfig!!
            }

            refreshCosConfig(folderType)
        }
    }

    /**
     * 刷新COS配置（从API获取临时密钥）
     */
    private suspend fun refreshCosConfig(folderType: Int = DEFAULT_FOLDER_TYPE): CosConfig =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Refreshing COS config...")
                val response =
                    apiService.getUploadToken(UploadTokenParamModel(folderType = folderType))

                if (response.isSuccess() && response.data != null) {
                    val token = response.data
                    val config = token.toCosConfig()
                    configCache.update(config)
                    Log.d(
                        TAG,
                        "COS config refreshed successfully, expires at: ${config.expiredTime}"
                    )
                    config
                } else {
                    throw Exception("Failed to get upload token: ${response.resultMsg}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh COS config", e)
                throw e
            }
        }

    /**
     * 清除缓存的服务和配置（用于错误重试）
     */
    private suspend fun clearCache() {
        // 分别清除服务和配置缓存，避免嵌套锁
        serviceMutex.withLock {
            serviceRef.set(null)
        }
        configMutex.withLock {
            configCache.clear()
        }
        Log.d(TAG, "Cache cleared")
    }

    // ==================== 公共接口实现 ====================
    override suspend fun uploadFile(params: UploadParams): CosUploadResult {
        return executeWithRetry {
            uploadFileInternal(params, null)
        }
    }

    override suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return executeWithRetry {
            uploadFileInternal(params, onProgress)
        }
    }

    override suspend fun deleteFile(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig()

            val request = DeleteObjectRequest(config.bucket, key)
            service.deleteObject(request)

            Log.d(TAG, "File deleted successfully: $key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $key", e)
            false
        }
    }

    override suspend fun fileExists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig()

            val request = HeadObjectRequest(config.bucket, key)
            service.headObject(request)
            true
        } catch (e: CosXmlServiceException) {
            e.statusCode != 404
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence: $key", e)
            false
        }
    }

    override suspend fun getFileSize(key: String): Long? = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig()

            val request = HeadObjectRequest(config.bucket, key)
            val result = service.headObject(request)
            result.headers?.get("Content-Length")?.firstOrNull()?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size for: $key", e)
            null
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行操作并支持重试机制
     */
    private suspend fun <T> executeWithRetry(operation: suspend () -> T): T {
        return try {
            operation()
        } catch (e: Exception) {
            Log.w(TAG, "Operation failed, attempting retry after cache clear", e)
            try {
                clearCache()
                operation()
            } catch (retryException: Exception) {
                Log.e(TAG, "Operation failed after retry", retryException)
                throw retryException
            }
        }
    }

    /**
     * 内部上传实现
     */
    private suspend fun uploadFileInternal(
        params: UploadParams,
        onProgress: ((UploadProgress) -> Unit)?
    ): CosUploadResult = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(params.folderType)
            val key = params.key.takeIf { it.isNotBlank() } ?: CosUtils.generateFileKey(
                config.fileKeyPre,
                params.fileUri
            )
            val request = PutObjectRequest(config.bucket, key, params.fileUri)
            val newParams = params.copy(key = key)
            // 设置Content-Type
            newParams.contentType?.let {
                request.setRequestHeaders("Content-Type", it, false)
            }

            // 设置进度回调
            onProgress?.let { callback ->
                request.setProgressListener { complete, target ->
                    callback(UploadProgress(complete, target))
                }
            }

            // 执行上传
            service.putObject(request)

            CosUploadResult(
                success = true,
                key = newParams.key,
                bucket = config.bucket,
                region = config.region,
                url = getPublicUrl(service, newParams, config)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for key: ${params.key}", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        }
    }

    /**
     * 通过接口获取文件访问URL
     */
    private suspend fun getPublicUrl(
        service: CosXmlService,
        params: UploadParams,
        config: CosConfig
    ): String {
        val fallbackUrl = service.getObjectUrl(config.bucket, config.region, params.key)
        Log.e("getPublicUrl", "fallbackUrl: $fallbackUrl")
        return try {
            val fileSize = params.fileUri.getFileSize(context)
            val saveFileParam = SaveFileParamModel(
                folderType = params.folderType,
                fileKey = params.key,
                fileSize = fileSize
            )
            val response = apiService.getFileUrl(saveFileParam)
            if (response.isSuccess()) {
                Log.e("getPublicUrl", "response.data: ${response.data}")
                response.data ?: fallbackUrl
            } else {
                Log.w(TAG, "Failed to get file URL from API, using fallback")
                fallbackUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file URL from API, using fallback", e)
            fallbackUrl
        }
    }
}