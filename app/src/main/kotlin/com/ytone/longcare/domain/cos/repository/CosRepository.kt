package com.ytone.longcare.domain.cos.repository

import com.ytone.longcare.data.cos.model.CosConfig
import com.ytone.longcare.data.cos.model.CosCredentials
import com.ytone.longcare.data.cos.model.CosUploadResult
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.data.cos.model.UploadProgress
import kotlinx.coroutines.flow.Flow

/**
 * COS存储服务接口
 * 提供文件上传、下载、删除等功能
 */
interface CosRepository {
    
    /**
     * 初始化COS服务
     * @param config COS配置信息
     */
    suspend fun initCosService(config: CosConfig)
    
    /**
     * 使用临时密钥初始化COS服务
     * @param credentials 临时密钥信息
     * @param region 地域
     * @param bucket 存储桶名称
     */
    suspend fun initCosServiceWithCredentials(
        credentials: CosCredentials,
        region: String,
        bucket: String
    )
    
    /**
     * 上传文件
     * @param params 上传参数
     * @return 上传结果
     */
    suspend fun uploadFile(params: UploadParams): CosUploadResult
    
    /**
     * 上传文件并监听进度
     * @param params 上传参数
     * @param onProgress 进度回调
     * @return 上传结果
     */
    suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult
    
    /**
     * 上传文件流
     * @param params 上传参数
     * @return 进度流和结果
     */
    fun uploadFileFlow(params: UploadParams): Flow<Result<UploadProgress>>
    
    /**
     * 删除文件
     * @param key 文件键名
     * @return 删除是否成功
     */
    suspend fun deleteFile(key: String): Boolean
    
    /**
     * 获取文件下载URL
     * @param key 文件键名
     * @param expireTimeInSeconds 过期时间（秒）
     * @return 下载URL
     */
    suspend fun getDownloadUrl(key: String, expireTimeInSeconds: Long = 3600): String?
    
    /**
     * 检查文件是否存在
     * @param key 文件键名
     * @return 文件是否存在
     */
    suspend fun fileExists(key: String): Boolean
    
    /**
     * 获取文件信息
     * @param key 文件键名
     * @return 文件大小（字节），如果文件不存在返回null
     */
    suspend fun getFileSize(key: String): Long?
}