package com.ytone.longcare.features.shared.cos

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.data.cos.config.CosConfigManager
import com.ytone.longcare.data.cos.model.CosCredentials
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import com.ytone.longcare.domain.cos.usecase.CosServiceManagerUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COS服务使用示例
 * 展示如何在业务层使用COS服务进行文件上传、下载等操作
 */
@Singleton
class CosServiceExample @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cosServiceManager: CosServiceManagerUseCase,
    private val cosConfigManager: CosConfigManager
) {
    
    companion object {
        private const val TAG = "CosServiceExample"
    }
    
    /**
     * 初始化COS服务
     * 注意：在实际使用中，临时密钥应该从服务器获取
     */
    suspend fun initializeCosService(
        region: String = "ap-beijing",
        bucket: String = "your-bucket-name"
    ): Boolean {
        return try {
            // 设置基本配置
            cosConfigManager.setBasicConfig(region, bucket)
            
            // 在实际应用中，这里应该调用服务器API获取临时密钥
            val credentials = getTemporaryCredentialsFromServer()
            
            if (credentials != null) {
                cosConfigManager.setCredentials(credentials)
                cosServiceManager.initService(credentials, region, bucket)
                Log.d(TAG, "COS service initialized successfully")
                true
            } else {
                Log.e(TAG, "Failed to get temporary credentials")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize COS service", e)
            false
        }
    }
    
    /**
     * 上传单个文件
     * @param filePath 文件路径
     * @param category 文件分类（如：avatar, document等）
     * @param userId 用户ID
     * @return 上传结果
     */
    suspend fun uploadFile(
        filePath: String,
        category: String,
        userId: String
    ): CosUploadResult {
        return try {
            // 检查配置是否有效
            if (!cosConfigManager.isConfigValid()) {
                return CosUploadResult(
                    success = false,
                    errorMessage = "COS配置无效或已过期：${cosConfigManager.getConfigStatus()}"
                )
            }
            
            // 生成上传参数
            val prefix = cosConfigManager.createUploadPrefix(userId, category)
            val uploadParams = CosUtils.createUploadParams(filePath, prefix)
            
            Log.d(TAG, "Uploading file: ${uploadParams.key}")
            
            // 执行上传
            val result = cosServiceManager.uploadFile(uploadParams)
            
            if (result.success) {
                Log.d(TAG, "File uploaded successfully: ${result.url}")
            } else {
                Log.e(TAG, "File upload failed: ${result.errorMessage}")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            CosUploadResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 上传文件并监听进度
     * @param filePath 文件路径
     * @param category 文件分类
     * @param userId 用户ID
     * @param onProgress 进度回调
     * @return 上传结果
     */
    suspend fun uploadFileWithProgress(
        filePath: String,
        category: String,
        userId: String,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                return CosUploadResult(
                    success = false,
                    errorMessage = "COS配置无效或已过期：${cosConfigManager.getConfigStatus()}"
                )
            }
            
            val prefix = cosConfigManager.createUploadPrefix(userId, category)
            val uploadParams = CosUtils.createUploadParams(filePath, prefix)
            
            Log.d(TAG, "Uploading file with progress: ${uploadParams.key}")
            
            cosServiceManager.uploadFileWithProgress(uploadParams, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file with progress", e)
            CosUploadResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 上传文件流（返回Flow）
     * @param filePath 文件路径
     * @param category 文件分类
     * @param userId 用户ID
     * @return 进度流
     */
    fun uploadFileFlow(
        filePath: String,
        category: String,
        userId: String
    ): Flow<Result<UploadProgress>> {
        val prefix = cosConfigManager.createUploadPrefix(userId, category)
        val uploadParams = CosUtils.createUploadParams(filePath, prefix)
        
        return cosServiceManager.uploadFileFlow(uploadParams)
            .onStart {
                Log.d(TAG, "Starting file upload flow: ${uploadParams.key}")
            }
            .onCompletion { throwable ->
                if (throwable == null) {
                    Log.d(TAG, "File upload flow completed successfully")
                } else {
                    Log.e(TAG, "File upload flow completed with error", throwable)
                }
            }
            .catch { e ->
                Log.e(TAG, "Error in file upload flow", e)
                emit(Result.failure(e))
            }
    }
    
    /**
     * 从Uri上传文件
     * @param uri 文件Uri
     * @param category 文件分类
     * @param userId 用户ID
     * @return 上传结果
     */
    suspend fun uploadFileFromUri(
        uri: Uri,
        category: String,
        userId: String
    ): CosUploadResult {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                return CosUploadResult(
                    success = false,
                    errorMessage = "COS配置无效或已过期：${cosConfigManager.getConfigStatus()}"
                )
            }
            
            val prefix = cosConfigManager.createUploadPrefix(userId, category)
            val uploadParams = CosUtils.createUploadParamsFromUri(context, uri, prefix)
            
            if (uploadParams == null) {
                return CosUploadResult(
                    success = false,
                    errorMessage = "无法从Uri获取文件信息"
                )
            }
            
            Log.d(TAG, "Uploading file from Uri: ${uploadParams.key}")
            
            val result = cosServiceManager.uploadFile(uploadParams)
            
            // 清理临时文件
            CosUtils.cleanTempFiles(context)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file from Uri", e)
            CosUploadResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 批量上传文件
     * @param filePaths 文件路径列表
     * @param category 文件分类
     * @param userId 用户ID
     * @param onProgress 进度回调（文件键名，进度）
     * @return 上传结果列表
     */
    suspend fun uploadMultipleFiles(
        filePaths: List<String>,
        category: String,
        userId: String,
        onProgress: ((String, UploadProgress) -> Unit)? = null
    ): List<CosUploadResult> {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                return filePaths.map {
                    CosUploadResult(
                        success = false,
                        errorMessage = "COS配置无效或已过期：${cosConfigManager.getConfigStatus()}"
                    )
                }
            }
            
            val prefix = cosConfigManager.createUploadPrefix(userId, category)
            val uploadParamsList = filePaths.map { filePath ->
                CosUtils.createUploadParams(filePath, prefix)
            }
            
            Log.d(TAG, "Uploading ${uploadParamsList.size} files")
            
            cosServiceManager.uploadFiles(uploadParamsList, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading multiple files", e)
            filePaths.map {
                CosUploadResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * 删除文件
     * @param key 文件键名
     * @return 删除是否成功
     */
    suspend fun deleteFile(key: String): Boolean {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                Log.e(TAG, "COS配置无效，无法删除文件")
                return false
            }
            
            val result = cosServiceManager.deleteFile(key)
            
            if (result) {
                Log.d(TAG, "File deleted successfully: $key")
            } else {
                Log.e(TAG, "Failed to delete file: $key")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $key", e)
            false
        }
    }
    
    /**
     * 获取文件下载链接
     * @param key 文件键名
     * @param expireTimeInSeconds 过期时间（秒）
     * @return 下载链接
     */
    suspend fun getDownloadUrl(key: String, expireTimeInSeconds: Long = 3600): String? {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                Log.e(TAG, "COS配置无效，无法获取下载链接")
                return null
            }
            
            cosServiceManager.getDownloadUrl(key, expireTimeInSeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL for: $key", e)
            null
        }
    }
    
    /**
     * 检查文件是否存在
     * @param key 文件键名
     * @return 文件是否存在
     */
    suspend fun fileExists(key: String): Boolean {
        return try {
            if (!cosConfigManager.isConfigValid()) {
                Log.e(TAG, "COS配置无效，无法检查文件存在性")
                return false
            }
            
            cosServiceManager.fileExists(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence: $key", e)
            false
        }
    }
    
    /**
     * 获取配置状态
     * @return 配置状态描述
     */
    fun getConfigStatus(): String {
        return cosConfigManager.getConfigStatus()
    }
    
    /**
     * 模拟从服务器获取临时密钥
     * 在实际应用中，这里应该调用真实的服务器API
     */
    private suspend fun getTemporaryCredentialsFromServer(): CosCredentials? {
        // TODO: 实现真实的服务器API调用
        // 这里返回null，表示需要实现服务器端获取临时密钥的逻辑
        Log.w(TAG, "getTemporaryCredentialsFromServer() 需要实现服务器API调用")
        return null
        
        // 示例代码（请勿在生产环境使用）：
        /*
        return CosCredentials(
            tmpSecretId = "your_tmp_secret_id",
            tmpSecretKey = "your_tmp_secret_key",
            sessionToken = "your_session_token",
            expiredTime = System.currentTimeMillis() / 1000 + 3600, // 1小时后过期
            startTime = System.currentTimeMillis() / 1000
        )
        */
    }
}