package com.ytone.longcare.data.cos.repository

import android.content.Context
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
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.getFileSize
import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import com.ytone.longcare.data.cos.model.toCosConfig
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW

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
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : CosRepository {

    companion object {
        private const val TAG = "CosRepositoryImpl"
        private const val TOKEN_REFRESH_THRESHOLD_SECONDS = 300L // 5分钟提前刷新
    }

    // 线程安全的状态管理 - 分离锁职责避免死锁
    private val serviceMutex = Mutex()  // 专门保护服务初始化
    private val configMutex = Mutex()   // 专门保护配置刷新
    private val serviceRef = AtomicReference<CosXmlService?>(null)
    private val configCache = CosConfigCache()

    /**
     * CosConfig缓存管理类
     * 统一管理COS配置信息，包括临时密钥、bucket、region等
     * 支持按folderType分别缓存不同的配置
     */
    private class CosConfigCache {
        private val configMap = ConcurrentHashMap<Int, CosConfig>()

        fun isValid(folderType: Int): Boolean {
            val config = configMap[folderType] ?: return false
            return !config.isExpiringSoon(TOKEN_REFRESH_THRESHOLD_SECONDS)
        }

        fun getConfig(folderType: Int): CosConfig? {
            return configMap[folderType]
        }

        fun clear() {
            configMap.clear()
        }

        fun clearForType(folderType: Int) {
            configMap.remove(folderType)
        }

        fun update(folderType: Int, config: CosConfig) {
            configMap[folderType] = config
        }

        fun updateFromToken(folderType: Int, token: UploadTokenResultModel) {
            configMap[folderType] = token.toCosConfig()
        }
    }

    /**
     * 动态密钥提供者 - 支持自动刷新临时密钥
     * 使用默认的folderType来获取凭证，适用于大多数场景
     */
    private inner class DynamicCredentialProvider(
        private val defaultFolderType: Int = CosConstants.DEFAULT_FOLDER_TYPE
    ) : QCloudCredentialProvider {

        override fun getCredentials(): QCloudLifecycleCredentials? {
            return try {
                // 检查并刷新配置
                if (!configCache.isValid(defaultFolderType)) {
                    refreshConfigSync()
                }

                configCache.getConfig(defaultFolderType)?.let { config ->
                    SessionQCloudCredentials(
                        config.tmpSecretId,
                        config.tmpSecretKey,
                        config.sessionToken,
                        config.startTime,
                        config.expiredTime
                    )
                }
            } catch (e: Exception) {
                logE("Failed to get credentials", tag = TAG, throwable = e)
                null
            }
        }

        override fun refresh() {
            try {
                refreshConfigSync()
                logD("Credentials refreshed successfully", tag = TAG)
            } catch (e: Exception) {
                logE("Failed to refresh credentials", tag = TAG, throwable = e)
            }
        }

        /**
         * 同步刷新配置（在密钥提供者回调中使用）
         */
        private fun refreshConfigSync() {
            runBlocking {
                refreshCosConfig(defaultFolderType)
            }
        }
    }

    /**
     * 获取有效的COS服务实例
     * 使用默认的folderType来初始化服务
     */
    private suspend fun getCosService(): CosXmlService {
        serviceRef.get()?.let { return it }

        return serviceMutex.withLock {
            // 双重检查
            serviceRef.get()?.let { return@withLock it }

            // 获取配置并初始化服务
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
            val service = createCosService(config)
            serviceRef.set(service)

            logD(
                "COS service initialized with bucket: ${config.bucket}, region: ${config.region}",
                tag = TAG
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
    private suspend fun getValidCosConfig(folderType: Int = CosConstants.DEFAULT_FOLDER_TYPE): CosConfig {
        if (configCache.isValid(folderType)) {
            return configCache.getConfig(folderType) 
                ?: throw IllegalStateException("Config cache is valid but config is null for folderType: $folderType")
        }

        return configMutex.withLock {
            // 双重检查
            if (configCache.isValid(folderType)) {
                return@withLock configCache.getConfig(folderType) 
                    ?: throw IllegalStateException("Config cache is valid but config is null for folderType: $folderType")
            }

            refreshCosConfig(folderType)
        }
    }

    /**
     * 刷新COS配置（从API获取临时密钥）
     */
    private suspend fun refreshCosConfig(folderType: Int = CosConstants.DEFAULT_FOLDER_TYPE): CosConfig =
        withContext(Dispatchers.IO) {
            try {
                logD("Refreshing COS config...", tag = TAG)
                val response = safeApiCall(ioDispatcher, eventBus) {
                    apiService.getUploadToken(UploadTokenParamModel(folderType = folderType))
                }
                when (response) {
                    is ApiResult.Exception -> {
                        throw Exception("Failed to get upload token: ${response.exception.message}")
                    }

                    is ApiResult.Failure -> {
                        throw Exception("Failed to get upload token: ${response.message}")
                    }

                    is ApiResult.Success -> {
                        val token = response.data
                        val config = token.toCosConfig()
                        configCache.update(folderType, config)
                        logD(
                            "COS config refreshed successfully for folderType: $folderType, expires at: ${config.expiredTime}",
                            tag = TAG
                        )
                        config
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("Failed to refresh COS config", tag = TAG, throwable = e)
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
        logD("Cache cleared", tag = TAG)
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
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)

            val request = DeleteObjectRequest(config.bucket, key)
            service.deleteObject(request)

            logD("File deleted successfully: $key", tag = TAG)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Failed to delete file: $key", tag = TAG, throwable = e)
            false
        }
    }

    override suspend fun fileExists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)

            val request = HeadObjectRequest(config.bucket, key)
            service.headObject(request)
            true
        } catch (e: CosXmlServiceException) {
            e.statusCode != 404
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Error checking file existence: $key", tag = TAG, throwable = e)
            false
        }
    }

    override suspend fun getFileSize(key: String): Long? = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)

            val request = HeadObjectRequest(config.bucket, key)
            val result = service.headObject(request)
            result.headers?.get("Content-Length")?.firstOrNull()?.toLongOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Failed to get file size for: $key", tag = TAG, throwable = e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW("Operation failed, attempting retry after cache clear", tag = TAG, throwable = e)
            try {
                clearCache()
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (retryException: Exception) {
                logE("Operation failed after retry", tag = TAG, throwable = retryException)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Upload failed for key: ${params.key}", tag = TAG, throwable = e)
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
        logD("Using fallbackUrl candidate: $fallbackUrl", tag = "getPublicUrl")
        return try {
            val fileSize = params.fileUri.getFileSize(context)
            val saveFileParam = SaveFileParamModel(
                folderType = params.folderType,
                fileKey = params.key,
                fileSize = fileSize
            )
            val response = apiService.getFileUrl(saveFileParam)
            if (response.isSuccess()) {
                logD("getFileUrl API returned url: ${response.data}", tag = "getPublicUrl")
                response.data ?: fallbackUrl
            } else {
                logW("Failed to get file URL from API, using fallback", tag = TAG)
                fallbackUrl
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Error getting file URL from API, using fallback", tag = TAG, throwable = e)
            fallbackUrl
        }
    }
}
