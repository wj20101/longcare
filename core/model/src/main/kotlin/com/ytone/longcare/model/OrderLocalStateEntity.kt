package com.ytone.longcare.model

/**
 * 订单本地状态实体 - 存储客户端维护的本地数据
 * 
 * 设计原则：
 * 1. 与 [OrderEntity] 1:1 关联
 * 2. 仅存储本地状态，不包含服务端数据
 * 3. 便于服务端数据整体覆盖更新时不影响本地状态
 */
data class OrderLocalStateEntity(
    val orderId: Long,
    
    // ========== 本地服务状态 ==========
    /**
     * 本地订单状态
     * @see LocalOrderStatus
     */
    val localStatus: Int = LocalOrderStatus.PENDING.value,
    
    val localStartTimestamp: Long? = null,
    
    val localEndTimestamp: Long? = null,
    
    // ========== 人脸验证状态 ==========
    val faceVerificationCompleted: Boolean = false,
    
    // ========== 同步标记 ==========
    val needsSync: Boolean = false,
    
    // ========== 时间戳 ==========
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取本地状态枚举
     */
    fun getLocalStatusEnum(): LocalOrderStatus = LocalOrderStatus.fromValue(localStatus)
}
