package com.ytone.longcare.domain.cos.repository

import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.UploadProgress
import com.ytone.longcare.model.CosStorageException

/**
 * COS存储服务接口
 * 提供文件上传、下载、删除等功能
 */
interface CosRepository {
    
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
     * 按需获取私有文件访问地址。
     *
     * 上传阶段只保存 [fileKey]；只有展示、下载等真正读取文件的场景才调用本方法。
     */
    suspend fun getFileUrl(
        fileKey: String,
        folderType: Int? = null,
        fileSize: Long? = null,
    ): String

    /**
     * 删除文件
     * @param key 文件键名
     * @return 删除是否成功；文件不存在返回 false，其它失败抛出 [CosStorageException]
     */
    suspend fun deleteFile(key: String): Boolean
    
    /**
     * 检查文件是否存在
     * @param key 文件键名
     * @return 文件是否存在；鉴权、网络或服务端失败抛出 [CosStorageException]
     */
    suspend fun fileExists(key: String): Boolean
    
    /**
     * 获取文件信息
     * @param key 文件键名
     * @return 文件大小（字节），文件不存在返回 null，其它失败抛出 [CosStorageException]
     */
    suspend fun getFileSize(key: String): Long?
}
