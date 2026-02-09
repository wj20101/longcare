package com.ytone.longcare.features.service.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.common.utils.logE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待处理订单数据类
 */
@JsonClass(generateAdapter = true)
data class PendingOrder(
    val orderId: Long,
    val serviceName: String,
    val serviceEndTime: Long
)

/**
 * 待处理订单存储管理器
 * 用于持久化存储待处理的服务时间结束通知订单
 */
@Singleton
class PendingOrdersStorage @Inject constructor(
    private val context: Context,
    private val prefsName: String = "pending_orders_storage"
) {

    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, PendingOrder::class.java)
    private val adapter = moshi.adapter<List<PendingOrder>>(listType)

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_PENDING_ORDERS = "pending_orders"
        private const val TAG = "PendingOrdersStorage"
    }

    /**
     * 添加待处理订单
     */
    fun addPendingOrder(orderId: Long, serviceName: String, serviceEndTime: Long) {
        try {
            val orders = getAllPendingOrders().toMutableList()
            
            // 检查是否已存在
            val existingIndex = orders.indexOfFirst { it.orderId == orderId }
            val newOrder = PendingOrder(orderId, serviceName, serviceEndTime)
            
            if (existingIndex != -1) {
                orders[existingIndex] = newOrder
                logI("更新待处理订单: orderId=$orderId")
            } else {
                orders.add(newOrder)
                logI("添加待处理订单: orderId=$orderId")
            }
            
            saveOrders(orders)
        } catch (e: Exception) {
            logE("添加待处理订单失败: ${e.message}")
        }
    }

    /**
     * 获取所有待处理订单
     */
    fun getAllPendingOrders(): List<PendingOrder> {
        return try {
            val json = sharedPreferences.getString(KEY_PENDING_ORDERS, null)
            if (json != null) {
                adapter.fromJson(json) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logE("获取待处理订单失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 移除待处理订单
     */
    fun removePendingOrder(orderId: Long) {
        try {
            val orders = getAllPendingOrders().toMutableList()
            val removed = orders.removeIf { it.orderId == orderId }
            
            if (removed) {
                saveOrders(orders)
                logI("移除待处理订单: orderId=$orderId")
            }
        } catch (e: Exception) {
            logE("移除待处理订单失败: ${e.message}")
        }
    }

    /**
     * 清理过期订单
     */
    fun cleanupExpiredOrders() {
        try {
            val currentTime = System.currentTimeMillis()
            val orders = getAllPendingOrders()
            val validOrders = orders.filter { it.serviceEndTime > currentTime }
            
            if (validOrders.size < orders.size) {
                saveOrders(validOrders)
                logI("清理过期订单完成，保留 ${validOrders.size} 个有效订单")
            }
        } catch (e: Exception) {
            logE("清理过期订单失败: ${e.message}")
        }
    }

    /**
     * 清空所有待处理订单
     */
    fun clearAllPendingOrders() {
        try {
            sharedPreferences.edit { remove(KEY_PENDING_ORDERS) }
            logI("清空所有待处理订单")
        } catch (e: Exception) {
            logE("清空待处理订单失败: ${e.message}")
        }
    }

    /**
     * 保存订单列表到SharedPreferences
     */
    private fun saveOrders(orders: List<PendingOrder>) {
        try {
            val json = adapter.toJson(orders)
            sharedPreferences.edit { putString(KEY_PENDING_ORDERS, json) }
        } catch (e: Exception) {
            logE("保存待处理订单失败: ${e.message}")
        }
    }
}
