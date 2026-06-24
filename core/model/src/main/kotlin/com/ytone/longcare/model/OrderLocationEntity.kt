package com.ytone.longcare.model

/**
 * 订单定位记录实体
 * 
 * 存储服务过程中的位置记录，用于：
 * 1. 轨迹记录
 * 2. 服务区域验证
 * 3. 位置上报
 */
data class OrderLocationEntity(
    val id: Long = 0L,
    
    val orderId: Long,
    
    // ========== 位置信息 ==========
    val latitude: Double,
    
    val longitude: Double,
    
    val accuracy: Float = 0f,
    
    val provider: String = "",

    val coordType: String = "",

    val locationType: Int = 0,

    val trustedLevel: Int = 0,

    val locationTime: Long = 0L,
    
    // ========== 上传状态 ==========
    /**
     * 上传状态
     * @see LocationUploadStatus
     */
    val uploadStatus: Int = LocationUploadStatus.PENDING.value,
    
    // ========== 时间戳 ==========
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取上传状态枚举
     */
    fun getUploadStatusEnum(): LocationUploadStatus = LocationUploadStatus.fromValue(uploadStatus)
}
