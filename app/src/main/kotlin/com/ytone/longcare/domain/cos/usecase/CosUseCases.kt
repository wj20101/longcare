package com.ytone.longcare.domain.cos.usecase

import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosCredentials
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import com.ytone.longcare.domain.cos.repository.CosRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 初始化COS服务UseCase
 */
@Singleton
class InitCosServiceUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(config: CosConfig) {
        cosRepository.initCosService(config)
    }
    
    suspend operator fun invoke(
        credentials: CosCredentials,
        region: String,
        bucket: String
    ) {
        cosRepository.initCosServiceWithCredentials(credentials, region, bucket)
    }
}

/**
 * 上传文件UseCase
 */
@Singleton
class UploadFileUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(params: UploadParams): CosUploadResult {
        return cosRepository.uploadFile(params)
    }
    
    suspend fun uploadWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return cosRepository.uploadFileWithProgress(params, onProgress)
    }
    
    fun uploadFlow(params: UploadParams): Flow<Result<UploadProgress>> {
        return cosRepository.uploadFileFlow(params)
    }
}

/**
 * 删除文件UseCase
 */
@Singleton
class DeleteFileUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(key: String): Boolean {
        return cosRepository.deleteFile(key)
    }
}

/**
 * 获取下载链接UseCase
 */
@Singleton
class GetDownloadUrlUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(key: String, expireTimeInSeconds: Long = 3600): String? {
        return cosRepository.getDownloadUrl(key, expireTimeInSeconds)
    }
}

/**
 * 检查文件是否存在UseCase
 */
@Singleton
class CheckFileExistsUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(key: String): Boolean {
        return cosRepository.fileExists(key)
    }
}

/**
 * 获取文件大小UseCase
 */
@Singleton
class GetFileSizeUseCase @Inject constructor(
    private val cosRepository: CosRepository
) {
    suspend operator fun invoke(key: String): Long? {
        return cosRepository.getFileSize(key)
    }
}

/**
 * COS服务管理UseCase
 * 提供统一的COS服务管理功能
 */
@Singleton
class CosServiceManagerUseCase @Inject constructor(
    private val initCosServiceUseCase: InitCosServiceUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val getDownloadUrlUseCase: GetDownloadUrlUseCase,
    private val checkFileExistsUseCase: CheckFileExistsUseCase,
    private val getFileSizeUseCase: GetFileSizeUseCase
) {
    
    /**
     * 初始化COS服务
     */
    suspend fun initService(config: CosConfig) {
        initCosServiceUseCase(config)
    }
    
    suspend fun initService(
        credentials: CosCredentials,
        region: String,
        bucket: String
    ) {
        initCosServiceUseCase(credentials, region, bucket)
    }
    
    /**
     * 上传文件
     */
    suspend fun uploadFile(params: UploadParams): CosUploadResult {
        return uploadFileUseCase(params)
    }
    
    suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return uploadFileUseCase.uploadWithProgress(params, onProgress)
    }
    
    fun uploadFileFlow(params: UploadParams): Flow<Result<UploadProgress>> {
        return uploadFileUseCase.uploadFlow(params)
    }
    
    /**
     * 删除文件
     */
    suspend fun deleteFile(key: String): Boolean {
        return deleteFileUseCase(key)
    }
    
    /**
     * 获取下载链接
     */
    suspend fun getDownloadUrl(key: String, expireTimeInSeconds: Long = 3600): String? {
        return getDownloadUrlUseCase(key, expireTimeInSeconds)
    }
    
    /**
     * 检查文件是否存在
     */
    suspend fun fileExists(key: String): Boolean {
        return checkFileExistsUseCase(key)
    }
    
    /**
     * 获取文件大小
     */
    suspend fun getFileSize(key: String): Long? {
        return getFileSizeUseCase(key)
    }
    
    /**
     * 批量上传文件
     */
    suspend fun uploadFiles(
        fileParams: List<UploadParams>,
        onProgress: ((String, UploadProgress) -> Unit)? = null
    ): List<CosUploadResult> {
        return fileParams.map { params ->
            if (onProgress != null) {
                uploadFileWithProgress(params) { progress ->
                    onProgress(params.key, progress)
                }
            } else {
                uploadFile(params)
            }
        }
    }
    
    /**
     * 批量删除文件
     */
    suspend fun deleteFiles(keys: List<String>): Map<String, Boolean> {
        return keys.associateWith { key ->
            deleteFile(key)
        }
    }
}