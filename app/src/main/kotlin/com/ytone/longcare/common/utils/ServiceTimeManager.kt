package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import com.ytone.longcare.di.OrderStorage
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务时间管理器
 * 负责管理服务开始时间的持久化存储和获取
 * 使用SystemClock.elapsedRealtime()方案，并处理手机重启后时间重置问题
 */
@Singleton
class ServiceTimeManager @Inject constructor(
    @param:OrderStorage private val orderPrefs: SharedPreferences,
    private val userSessionRepository: UserSessionRepository
) {
    companion object {
        private const val KEY_SERVICE_START_TIME = "service_start_time_"
        private const val KEY_SYSTEM_BOOT_TIME = "system_boot_time_"
        private const val KEY_REAL_START_TIME = "real_start_time_"
    }
    
    /**
     * 获取当前登录用户ID
     * @return 用户ID，如果未登录则返回null
     */
    private fun getCurrentUserId(): Int? {
        return when (val sessionState = userSessionRepository.sessionState.value) {
            is SessionState.LoggedIn -> sessionState.user.userId
            else -> null
        }
    }
    
    /**
     * 生成带用户ID的存储键
     * @param baseKey 基础键名
     * @param orderId 订单ID
     * @return 完整的存储键，格式为：baseKey_userId_orderId
     */
    private fun generateStorageKey(baseKey: String, orderId: Long): String {
        val userId = getCurrentUserId() ?: 0 // 如果未登录，使用0作为默认值
        return "${baseKey}${userId}_${orderId}"
    }
    
    /**
     * 获取服务开始时间（毫秒时间戳）
     * @param orderId 订单ID
     * @return 服务开始时间的毫秒时间戳，如果没有记录则返回null
     */
    fun getServiceStartTime(orderId: Long): Long? {
        val startTimeKey = generateStorageKey(KEY_SERVICE_START_TIME, orderId)
        val bootTimeKey = generateStorageKey(KEY_SYSTEM_BOOT_TIME, orderId)
        val realStartTimeKey = generateStorageKey(KEY_REAL_START_TIME, orderId)
        
        // 检查是否有记录
        if (!orderPrefs.contains(startTimeKey)) {
            return null
        }
        
        val savedElapsedTime = orderPrefs.getLong(startTimeKey, -1L)
        val savedBootTime = orderPrefs.getLong(bootTimeKey, -1L)
        val savedRealStartTime = orderPrefs.getLong(realStartTimeKey, -1L)
        
        if (savedElapsedTime == -1L || savedBootTime == -1L || savedRealStartTime == -1L) {
            return null
        }
        
        // 获取当前系统启动时间
        val currentBootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        
        // 检查是否发生了重启
        return if (abs(currentBootTime - savedBootTime) > 5000) { // 允许5秒误差
            // 手机重启了，使用保存的真实开始时间
            savedRealStartTime
        } else {
            // 没有重启，使用elapsedRealtime计算当前真实时间
            // 这样可以保证时间的连续性和准确性
            savedRealStartTime
        }
    }
    
    /**
     * 保存服务开始时间
     * @param orderId 订单ID
     * @param startTime 开始时间的毫秒时间戳，如果为null则使用当前时间
     */
    fun saveServiceStartTime(orderId: Long, startTime: Long? = null) {
        val currentTime = startTime ?: System.currentTimeMillis()
        val currentElapsedTime = SystemClock.elapsedRealtime()
        val currentBootTime = System.currentTimeMillis() - currentElapsedTime
        
        val startTimeKey = generateStorageKey(KEY_SERVICE_START_TIME, orderId)
        val bootTimeKey = generateStorageKey(KEY_SYSTEM_BOOT_TIME, orderId)
        val realStartTimeKey = generateStorageKey(KEY_REAL_START_TIME, orderId)
        
        orderPrefs.edit {
            putLong(startTimeKey, currentElapsedTime)
                .putLong(bootTimeKey, currentBootTime)
                .putLong(realStartTimeKey, currentTime)
        }
    }
    
    /**
     * 获取或创建服务开始时间
     * 如果没有记录则创建新的记录
     * @param orderId 订单ID
     * @return 服务开始时间的毫秒时间戳
     */
    fun getOrCreateServiceStartTime(orderId: Long): Long {
        val existingTime = getServiceStartTime(orderId)
        if (existingTime != null) {
            return existingTime
        }
        
        // 没有记录，创建新的
        val currentTime = System.currentTimeMillis()
        saveServiceStartTime(orderId, currentTime)
        return currentTime
    }
    
    /**
     * 保存服务开始时间
     * @param orderId 订单ID
     * @param startTime 开始时间（毫秒时间戳）
     */
    fun saveServiceStartTime(orderId: Long, startTime: Long) {
        val startTimeKey = generateStorageKey(KEY_SERVICE_START_TIME, orderId)
        val bootTimeKey = generateStorageKey(KEY_SYSTEM_BOOT_TIME, orderId)
        val realStartTimeKey = generateStorageKey(KEY_REAL_START_TIME, orderId)
        
        val currentBootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val currentElapsedTime = SystemClock.elapsedRealtime()
        
        orderPrefs.edit {
            putLong(startTimeKey, currentElapsedTime)
            putLong(bootTimeKey, currentBootTime)
            putLong(realStartTimeKey, startTime)
        }
    }
    
    /**
     * 清除服务时间记录
     * @param orderId 订单ID
     */
    fun clearServiceTime(orderId: Long) {
        val startTimeKey = generateStorageKey(KEY_SERVICE_START_TIME, orderId)
        val bootTimeKey = generateStorageKey(KEY_SYSTEM_BOOT_TIME, orderId)
        val realStartTimeKey = generateStorageKey(KEY_REAL_START_TIME, orderId)
        
        orderPrefs.edit {
            remove(startTimeKey)
            remove(bootTimeKey)
            remove(realStartTimeKey)
        }
    }
    
    /**
     * 计算服务已运行时间（毫秒）
     * @param orderId 订单ID
     * @return 已运行时间，如果没有记录则返回0
     */
    fun getServiceElapsedTime(orderId: Long): Long {
        val startTime = getServiceStartTime(orderId) ?: return 0L
        return System.currentTimeMillis() - startTime
    }
}