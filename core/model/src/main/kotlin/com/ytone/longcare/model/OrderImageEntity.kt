package com.ytone.longcare.model

/**
 * 订单图片实体
 * 
 * 管理订单相关图片的上传状态，支持：
 * 1. 完整的状态机管理
 * 2. 上传失败重试
 * 3. 取消上传
 */
data class OrderImageEntity(
    val id: Long = 0L,
    
    val orderId: Long,
    
    // ========== 图片类型 ==========
    /**
     * 图片类型
     * @see ImageType
     */
    val imageType: Int,
    
    // ========== 本地信息 ==========
    val localUri: String,
    
    val localPath: String? = null,
    
    // ========== 上传状态 ==========
    /**
     * 上传状态
     * @see ImageUploadStatus
     */
    val uploadStatus: Int = ImageUploadStatus.PENDING.value,
    
    val cloudKey: String? = null,
    
    val cloudUrl: String? = null,
    
    // ========== 错误信息 ==========
    val errorMessage: String? = null,
    
    // ========== 时间戳 ==========
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取图片类型枚举
     */
    fun getImageTypeEnum(): ImageType = ImageType.fromValue(imageType)
    
    /**
     * 获取上传状态枚举
     */
    fun getUploadStatusEnum(): ImageUploadStatus = ImageUploadStatus.fromValue(uploadStatus)
}
