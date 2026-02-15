package com.ytone.longcare.data.database.entity

/**
 * 订单状态常量统一管理
 * 
 * 设计原则：
 * 1. 数据库存储使用Int类型，避免字符串比较的潜在问题
 * 2. 提供枚举类型供业务层使用，保证类型安全
 * 3. 提供Int和枚举之间的转换方法
 */

// ========== 本地订单状态 ==========

/**
 * 本地订单状态枚举
 */
enum class LocalOrderStatus(val value: Int) {
    /** 待开始 */
    PENDING(0),
    /** 服务中 */
    IN_PROGRESS(1),
    /** 已完成 */
    COMPLETED(2);
    
    companion object {
        fun fromValue(value: Int): LocalOrderStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

// ========== 图片上传状态 ==========

/**
 * 图片上传状态枚举
 */
enum class ImageUploadStatus(val value: Int) {
    /** 待上传 */
    PENDING(0),
    /** 上传中 */
    UPLOADING(1),
    /** 上传成功 */
    SUCCESS(2),
    /** 上传失败 */
    FAILED(3),
    /** 已取消 */
    CANCELLED(4);
    
    companion object {
        fun fromValue(value: Int): ImageUploadStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

// ========== 位置上传状态 ==========

/**
 * 位置上传状态枚举
 */
enum class LocationUploadStatus(val value: Int) {
    /** 待上传 */
    PENDING(0),
    /** 上传成功 */
    SUCCESS(1),
    /** 上传失败 */
    FAILED(2);
    
    companion object {
        fun fromValue(value: Int): LocationUploadStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

// ========== 图片类型 ==========

/**
 * 图片类型枚举
 */
enum class ImageType(val value: Int) {
    /** 客户照片 */
    CUSTOMER(0),
    /** 服务前照片 */
    BEFORE_CARE(1),
    /** 服务中照片 */
    CENTER_CARE(2),
    /** 服务后照片 */
    AFTER_CARE(3);
    
    companion object {
        fun fromValue(value: Int): ImageType {
            return entries.find { it.value == value } ?: CUSTOMER
        }
    }
}
