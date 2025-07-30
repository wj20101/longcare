package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ytone.longcare.di.OrderStorage
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 人脸验证状态管理器
 * 负责管理人脸验证状态的持久化存储和查询
 */
@Singleton
class FaceVerificationStatusManager @Inject constructor(
    @param:OrderStorage private val orderPrefs: SharedPreferences,
    private val userSessionRepository: UserSessionRepository
) {
    companion object {
        private const val KEY_FACE_VERIFICATION = "face_verification_"
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
     * @param orderId 订单ID
     * @return 完整的存储键，格式为：face_verification_userId_orderId
     */
    private fun generateStorageKey(orderId: Long): String {
        val userId = getCurrentUserId() ?: 0 // 如果未登录，使用0作为默认值
        return "${KEY_FACE_VERIFICATION}${userId}_${orderId}"
    }
    
    /**
     * 保存人脸验证完成状态
     * @param orderId 订单ID
     */
    fun saveFaceVerificationCompleted(orderId: Long) {
        val key = generateStorageKey(orderId)
        orderPrefs.edit {
            putBoolean(key, true)
        }
    }
    
    /**
     * 检查订单是否已完成人脸验证
     * @param orderId 订单ID
     * @return true表示已完成人脸验证，false表示未完成
     */
    fun isFaceVerificationCompleted(orderId: Long): Boolean {
        val key = generateStorageKey(orderId)
        return orderPrefs.getBoolean(key, false)
    }
    
    /**
     * 清除订单的人脸验证状态
     * @param orderId 订单ID
     */
    fun clearFaceVerificationStatus(orderId: Long) {
        val key = generateStorageKey(orderId)
        orderPrefs.edit {
            remove(key)
        }
    }
}