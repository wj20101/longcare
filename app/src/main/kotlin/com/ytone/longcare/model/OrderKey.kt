package com.ytone.longcare.model

import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.navigation.OrderNavParams
import kotlinx.serialization.Serializable

/**
 * 统一订单标识符
 * 
 * 作为应用内部统一的订单标识模型，便于：
 * 1. 类型安全：编译期检查，避免参数混淆
 * 2. 易扩展：未来添加字段只需修改此处
 * 3. 统一入口：所有Repository和ViewModel使用同一模型
 * 
 * @param orderId 订单ID
 * @param planId 计划ID，默认值为0，未来版本可能有意义
 */
@Serializable
data class OrderKey(
    val orderId: Long,
    val planId: Int = 0
) {
    /**
     * 用于缓存的唯一字符串Key
     * 格式：orderId_planId
     */
    val cacheKey: String get() = "${orderId}_${planId}"
    
    companion object {
        /**
         * 从缓存Key解析OrderKey
         * @param key 格式为"orderId_planId"的字符串
         * @return 解析成功返回OrderKey，失败返回null
         */
        fun fromCacheKey(key: String): OrderKey? {
            val parts = key.split("_")
            if (parts.size != 2) return null
            return OrderKey(
                orderId = parts[0].toLongOrNull() ?: return null,
                planId = parts[1].toIntOrNull() ?: return null
            )
        }
    }
}

// ========== 转换扩展函数 ==========

/**
 * OrderNavParams → OrderKey
 */
fun OrderNavParams.toOrderKey() = OrderKey(orderId, planId)

/**
 * OrderKey → OrderNavParams
 */
fun OrderKey.toNavParams() = OrderNavParams(orderId, planId)

/**
 * OrderInfoRequestModel → OrderKey
 */
fun OrderInfoRequestModel.toOrderKey() = OrderKey(orderId, planId)

/**
 * OrderKey → OrderInfoRequestModel
 */
fun OrderKey.toRequestModel() = OrderInfoRequestModel(orderId, planId)
