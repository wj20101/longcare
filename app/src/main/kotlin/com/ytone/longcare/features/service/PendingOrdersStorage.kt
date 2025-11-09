package com.ytone.longcare.features.service

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.DefaultMoshi

/**
 * 待处理订单存储
 * 用于持久化保存待处理的服务时间结束通知订单
 */
class PendingOrdersStorage(
    private val context: Context,
    private val moshi: Moshi = DefaultMoshi
) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    data class PendingOrder(
        val orderId: Long,
        val serviceName: String,
        val endTime: Long,
        val scheduledTime: Long = System.currentTimeMillis()
    )
    
    companion object {
        private const val PREFS_NAME = "pending_orders_prefs"
        private const val KEY_PENDING_ORDERS = "pending_orders"
    }
    
    /**
     * 添加待处理订单
     */
    fun addPendingOrder(orderId: Long, serviceName: String, endTime: Long) {
        val pendingOrders = getPendingOrders().toMutableList()
        
        // 移除已存在的相同订单ID
        pendingOrders.removeAll { it.orderId == orderId }
        
        // 添加新订单
        val newOrder = PendingOrder(orderId, serviceName, endTime)
        pendingOrders.add(newOrder)
        
        // 保存到SharedPreferences
        savePendingOrders(pendingOrders)
    }
    
    /**
     * 获取所有待处理订单
     */
    fun getPendingOrders(): List<PendingOrder> {
        val json = prefs.getString(KEY_PENDING_ORDERS, "[]") ?: "[]"
        
        return try {
            val type = Types.newParameterizedType(List::class.java, PendingOrder::class.java)
            val adapter = moshi.adapter<List<PendingOrder>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 移除待处理订单
     */
    fun removePendingOrder(orderId: Long) {
        val pendingOrders = getPendingOrders().toMutableList()
        pendingOrders.removeAll { it.orderId == orderId }
        savePendingOrders(pendingOrders)
    }
    
    /**
     * 清理过期的待处理订单
     */
    fun cleanupExpiredOrders() {
        val currentTime = System.currentTimeMillis()
        val pendingOrders = getPendingOrders().toMutableList()
        
        // 移除已过期的订单（结束时间超过24小时）
        pendingOrders.removeAll { 
            currentTime - it.endTime > 24 * 60 * 60 * 1000 
        }
        
        savePendingOrders(pendingOrders)
    }
    
    /**
     * 清空所有待处理订单
     */
    fun clearAllPendingOrders() {
        prefs.edit().remove(KEY_PENDING_ORDERS).apply()
    }
    
    /**
     * 保存待处理订单列表
     */
    private fun savePendingOrders(orders: List<PendingOrder>) {
        try {
            val type = Types.newParameterizedType(List::class.java, PendingOrder::class.java)
            val adapter = moshi.adapter<List<PendingOrder>>(type)
            val json = adapter.toJson(orders)
            prefs.edit().putString(KEY_PENDING_ORDERS, json).apply()
        } catch (e: Exception) {
            // 忽略序列化错误
        }
    }
}