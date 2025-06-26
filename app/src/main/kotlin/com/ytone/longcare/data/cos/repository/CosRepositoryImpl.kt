package com.ytone.longcare.data.cos.repository

import android.content.Context
import android.util.Log
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.CosXmlServiceConfig
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.model.`object`.DeleteObjectRequest
import com.tencent.cos.xml.model.`object`.HeadObjectRequest
import com.tencent.cos.xml.model.`object`.PutObjectRequest
import com.tencent.qcloud.core.auth.SessionQCloudCredentials
import com.tencent.qcloud.core.auth.StaticCredentialProvider
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.response.UploadTokenResultModel
import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosCredentials
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CosRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: LongCareApiService
) : CosRepository {
    
    companion object {
        private const val TAG = "CosRepositoryImpl"
        private const val TOKEN_REFRESH_THRESHOLD_SECONDS = 300 // 5分钟提前刷新
    }
    
    private var cosXmlService: CosXmlService? = null
    private var currentBucket: String? = null
    private var currentRegion: String? = null
    
    // 缓存相关属性
    private var cachedTokenResult: UploadTokenResultModel? = null
    private var tokenExpireTime: Long = 0L
    private val tokenLock = Any()
    
    /**
     * 获取有效的上传token，支持缓存和自动刷新
     */
    private suspend fun getValidUploadToken(): UploadTokenResultModel {
        synchronized(tokenLock) {
            val currentTime = System.currentTimeMillis() / 1000
            
            // 检查缓存是否有效（提前5分钟刷新）
            if (cachedTokenResult != null && currentTime < (tokenExpireTime - TOKEN_REFRESH_THRESHOLD_SECONDS)) {
                return cachedTokenResult!!
            }
        }
        
        // 获取新的token
        return refreshUploadToken()
    }
    
    /**
     * 刷新上传token
     */
    private suspend fun refreshUploadToken(): UploadTokenResultModel = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing upload token...")
            val response = apiService.getUploadToken()
            
            if (response.isSuccess() && response.data != null) {
                val tokenResult = response.data
                
                synchronized(tokenLock) {
                    cachedTokenResult = tokenResult
                    // 解析过期时间
                    tokenExpireTime = try {
                        tokenResult.expiredTime.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3600)
                    } catch (_: Exception) {
                        System.currentTimeMillis() / 1000 + 3600 // 默认1小时后过期
                    }
                }
                
                Log.d(TAG, "Upload token refreshed successfully, expires at: $tokenExpireTime")
                tokenResult
            } else {
                throw Exception("Failed to get upload token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh upload token", e)
            throw e
        }
    }
    
    /**
     * 懒汉式获取COS服务实例
     */
    private suspend fun getCosService(): CosXmlService {
        cosXmlService?.let { return it }
        
        // 获取token并初始化服务
        val tokenResult = getValidUploadToken()
        return initCosServiceWithToken(tokenResult)
    }
    
    /**
     * 使用token初始化COS服务
     */
    private suspend fun initCosServiceWithToken(tokenResult: UploadTokenResultModel): CosXmlService = withContext(Dispatchers.IO) {
        try {
            val credentials = SessionQCloudCredentials(
                tokenResult.tmpSecretId,
                tokenResult.tmpSecretKey,
                tokenResult.sessionToken,
                tokenResult.startTime.toLongOrNull() ?: 0L,
                tokenResult.expiredTime.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + 3600)
            )
            
            val credentialProvider = StaticCredentialProvider(credentials)
            val serviceConfig = CosXmlServiceConfig.Builder()
                .setRegion(tokenResult.region)
                .isHttps(true)
                .builder()
            
            val service = CosXmlService(context, serviceConfig, credentialProvider)
            
            // 更新缓存的服务实例和配置
            cosXmlService = service
            currentBucket = tokenResult.bucket
            currentRegion = tokenResult.region
            
            Log.d(TAG, "COS service initialized with token, bucket: ${tokenResult.bucket}, region: ${tokenResult.region}")
            service
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize COS service with token", e)
            throw e
        }
    }
    
    override suspend fun initCosService(config: CosConfig): Unit = withContext(Dispatchers.IO) {
        try {
            val credentials = SessionQCloudCredentials(config.secretId, config.secretKey, null, 0, System.currentTimeMillis() / 1000 + 600)
            val credentialProvider = StaticCredentialProvider(credentials)
            val serviceConfig = CosXmlServiceConfig.Builder()
                .setRegion(config.region)
                .isHttps(true)
                .builder()
            
            cosXmlService = CosXmlService(context, serviceConfig, credentialProvider)
            currentBucket = config.bucket
            currentRegion = config.region
            
            Log.d(TAG, "COS service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize COS service", e)
            throw e
        }
    }
    
    override suspend fun initCosServiceWithCredentials(
        credentials: CosCredentials,
        region: String,
        bucket: String
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val sessionCredentials = SessionQCloudCredentials(
                credentials.tmpSecretId,
                credentials.tmpSecretKey,
                credentials.sessionToken,
                credentials.startTime,
                credentials.expiredTime
            )
            val credentialProvider = StaticCredentialProvider(sessionCredentials)
            val serviceConfig = CosXmlServiceConfig.Builder()
                .setRegion(region)
                .isHttps(true)
                .builder()
            
            cosXmlService = CosXmlService(context, serviceConfig, credentialProvider)
            currentBucket = bucket
            currentRegion = region
            
            Log.d(TAG, "COS service initialized with temporary credentials")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize COS service with credentials", e)
            throw e
        }
    }
    
    override suspend fun uploadFile(params: UploadParams): CosUploadResult = withContext(Dispatchers.IO) {
        try {
            return@withContext uploadFileInternal(params)
        } catch (e: Exception) {
            // 如果上传失败，尝试刷新token重试一次
            Log.w(TAG, "Upload failed, attempting to refresh token and retry", e)
            try {
                clearCachedService()
                return@withContext uploadFileInternal(params)
            } catch (retryException: Exception) {
                Log.e(TAG, "Upload failed after retry", retryException)
                return@withContext CosUploadResult(
                    success = false,
                    key = params.key,
                    errorMessage = "Upload failed: ${retryException.message}"
                )
            }
        }
    }
    
    private suspend fun uploadFileInternal(params: UploadParams): CosUploadResult {
        val service = getCosService()
        val bucket = currentBucket ?: throw IllegalStateException("Bucket not set")
        
        return try {
            val request = PutObjectRequest(bucket, params.key, params.filePath)
            
            // 设置元数据
            if (params.contentType != null) {
                request.setRequestHeaders("Content-Type", params.contentType, false)
            }
            
            val result = service.putObject(request)
            
            CosUploadResult(
                success = true,
                key = params.key,
                url = getPublicUrl(params.key)
            )
        } catch (e: CosXmlServiceException) {
            Log.e(TAG, "COS service error during upload: ${e.errorCode}", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.errorMessage}"
            )
        } catch (e: CosXmlClientException) {
            Log.e(TAG, "COS client error during upload", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upload", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        }
    }
    
    override suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult = withContext(Dispatchers.IO) {
        try {
            return@withContext uploadFileWithProgressInternal(params, onProgress)
        } catch (e: Exception) {
            // 如果上传失败，尝试刷新token重试一次
            Log.w(TAG, "Upload with progress failed, attempting to refresh token and retry", e)
            try {
                clearCachedService()
                return@withContext uploadFileWithProgressInternal(params, onProgress)
            } catch (retryException: Exception) {
                Log.e(TAG, "Upload with progress failed after retry", retryException)
                return@withContext CosUploadResult(
                    success = false,
                    key = params.key,
                    errorMessage = "Upload failed: ${retryException.message}"
                )
            }
        }
    }
    
    private suspend fun uploadFileWithProgressInternal(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        val service = getCosService()
        val bucket = currentBucket ?: throw IllegalStateException("Bucket not set")
        
        return try {
            val request = PutObjectRequest(bucket, params.key, params.filePath)
            
            // 设置元数据
            if (params.contentType != null) {
                request.setRequestHeaders("Content-Type", params.contentType, false)
            }
            
            // 设置进度回调
            request.setProgressListener { complete, target ->
                val progress = UploadProgress(
                    bytesTransferred = complete,
                    totalBytes = target
                )
                onProgress(progress)
            }
            
            val result = service.putObject(request)
            
            CosUploadResult(
                success = true,
                key = params.key,
                url = getPublicUrl(params.key)
            )
        } catch (e: CosXmlServiceException) {
            Log.e(TAG, "COS service error during upload: ${e.errorCode}", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.errorMessage}"
            )
        } catch (e: CosXmlClientException) {
            Log.e(TAG, "COS client error during upload", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upload", e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        }
    }
    
    override fun uploadFileFlow(
        params: UploadParams
    ): Flow<Result<UploadProgress>> = flow {
        try {
            uploadFileFlowInternal(params)
        } catch (e: Exception) {
            // 如果上传失败，尝试刷新token重试一次
            Log.w(TAG, "Upload flow failed, attempting to refresh token and retry", e)
            try {
                clearCachedService()
                uploadFileFlowInternal(params)
            } catch (retryException: Exception) {
                Log.e(TAG, "Upload flow failed after retry", retryException)
                emit(Result.failure(Exception("Upload failed: ${retryException.message}")))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private suspend fun FlowCollector<Result<UploadProgress>>.uploadFileFlowInternal(params: UploadParams) {
        val service = getCosService()
        val bucket = currentBucket ?: throw IllegalStateException("Bucket not set")
        
        try {
            val request = PutObjectRequest(bucket, params.key, params.filePath)
            
            // 设置元数据
            if (params.contentType != null) {
                request.setRequestHeaders("Content-Type", params.contentType, false)
            }
            
            // 设置进度回调
            request.setProgressListener { complete, target ->
                val progress = UploadProgress(
                    bytesTransferred = complete,
                    totalBytes = target
                )
                // 注意：这里不能直接 emit，因为不在协程上下文中
                // 进度回调会在上传过程中自动触发
            }
            
            val result = service.putObject(request)
            
            // 上传完成，发送最终进度
            val file = File(params.filePath)
            val fileSize = if (file.exists()) file.length() else 0L
            val finalProgress = UploadProgress(
                bytesTransferred = fileSize,
                totalBytes = fileSize
            )
            emit(Result.success(finalProgress))
        } catch (e: CosXmlServiceException) {
            Log.e(TAG, "COS service error during upload: ${e.errorCode}", e)
            emit(Result.failure(Exception("Upload failed: ${e.errorMessage}")))
        } catch (e: CosXmlClientException) {
            Log.e(TAG, "COS client error during upload", e)
            emit(Result.failure(Exception("Upload failed: ${e.message}")))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upload", e)
            emit(Result.failure(Exception("Upload failed: ${e.message}")))
        }
    }
    
    override suspend fun deleteFile(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val bucket = currentBucket ?: return@withContext false
            
            val deleteObjectRequest = DeleteObjectRequest(bucket, key)
            service.deleteObject(deleteObjectRequest)
            Log.d(TAG, "File deleted successfully: $key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $key", e)
            false
        }
    }
    
    override suspend fun getDownloadUrl(key: String, expireTimeInSeconds: Long): String? = withContext(Dispatchers.IO) {
        try {
            // 对于公共读的存储桶，直接返回公共 URL
            getPublicUrl(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL for: $key", e)
            null
        }
    }
    
    override suspend fun fileExists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val bucket = currentBucket ?: return@withContext false
            
            val request = HeadObjectRequest(bucket, key)
            service.headObject(request)
            true
        } catch (e: CosXmlServiceException) {
            if (e.statusCode == 404) {
                false
            } else {
                Log.e(TAG, "Error checking file existence: $key", e)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence: $key", e)
            false
        }
    }
    
    override suspend fun getFileSize(key: String): Long? = withContext(Dispatchers.IO) {
        try {
            val service = getCosService()
            val bucket = currentBucket ?: return@withContext null
            
            val request = HeadObjectRequest(bucket, key)
            val result = service.headObject(request)
            // 尝试从响应头获取文件大小
            val contentLengthStr = result.headers?.get("Content-Length") as? String
            contentLengthStr?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size for: $key", e)
            null
        }
    }
    
    /**
     * 清除缓存的服务实例，用于错误重试
     */
    private fun clearCachedService() {
        synchronized(tokenLock) {
            cosXmlService = null
            cachedTokenResult = null
            tokenExpireTime = 0L
        }
        Log.d(TAG, "Cached COS service and token cleared")
    }
    
    private fun getPublicUrl(key: String): String {
        val bucket = currentBucket ?: throw IllegalStateException("Bucket not set")
        val region = currentRegion ?: throw IllegalStateException("Region not set")
        return "https://$bucket.cos.$region.myqcloud.com/$key"
    }
}