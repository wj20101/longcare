package com.ytone.longcare.model

/**
 * 订单老人信息实体 - 存储老人相关信息
 * 
 * 设计原则：
 * 1. 与 [OrderEntity] 1:1 关联
 * 2. 老人信息独立存储，可单独更新
 * 3. 便于后续扩展老人相关属性
 */
data class OrderElderInfoEntity(
    val orderId: Long,
    
    // ========== 老人基本信息 ==========
    val elderUserId: Int = 0,
    
    val elderName: String = "",
    
    val elderIdCard: String = "",
    
    val elderAge: Int = 0,
    
    val elderGender: String = "",
    
    // ========== 老人地址信息 ==========
    val elderAddress: String = "",
    
    val elderLng: String = "",
    
    val elderLat: String = "",
    
    // ========== 服务统计信息 ==========
    val lastServiceTime: String = "",
    
    val monthServiceTime: Int = 0,
    
    val monthNoServiceTime: Int = 0,
    
    // ========== 时间戳 ==========
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis()
)
