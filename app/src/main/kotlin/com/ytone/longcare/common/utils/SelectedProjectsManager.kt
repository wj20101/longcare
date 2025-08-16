package com.ytone.longcare.common.utils

import android.content.SharedPreferences
import com.ytone.longcare.di.OrderStorage
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * 选中项目管理器
 * 用于管理订单中选中的项目ID列表的本地存储
 */
@Singleton
class SelectedProjectsManager @Inject constructor(
    @param:OrderStorage private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val KEY_SELECTED_PROJECTS = "selected_projects"
        private const val KEY_ORDER_ID = "order_id"
    }

    /**
     * 保存选中的项目ID列表
     * @param orderId 订单ID
     * @param selectedProjectIds 选中的项目ID列表
     */
    fun saveSelectedProjects(orderId: Long, selectedProjectIds: List<Int>) {
        val json = selectedProjectIds.toJsonString()
        sharedPreferences.edit {
            putString(getSelectedProjectsKey(orderId), json)
                .putLong(KEY_ORDER_ID, orderId)
        }
    }

    /**
     * 获取选中的项目ID列表
     * @param orderId 订单ID
     * @return 选中的项目ID列表，如果没有数据则返回null
     */
    fun getSelectedProjects(orderId: Long): List<Int>? {
        val json = sharedPreferences.getString(getSelectedProjectsKey(orderId), null)
        return json?.fromJsonToList<Int>()
    }

    /**
     * 清除指定订单的选中项目数据
     * @param orderId 订单ID
     */
    fun clearSelectedProjects(orderId: Long) {
        sharedPreferences.edit {
            remove(getSelectedProjectsKey(orderId))
        }
    }

    /**
     * 清除所有选中项目数据
     */
    fun clearAllSelectedProjects() {
        val currentOrderId = sharedPreferences.getLong(KEY_ORDER_ID, -1L)
        if (currentOrderId != -1L) {
            clearSelectedProjects(currentOrderId)
        }
        sharedPreferences.edit {
            remove(KEY_ORDER_ID)
        }
    }

    /**
     * 获取当前订单ID
     * @return 当前订单ID，如果没有则返回null
     */
    fun getCurrentOrderId(): Long? {
        val orderId = sharedPreferences.getLong(KEY_ORDER_ID, -1L)
        return if (orderId != -1L) orderId else null
    }

    /**
     * 检查是否有选中项目数据
     * @param orderId 订单ID
     * @return 是否有数据
     */
    fun hasSelectedProjects(orderId: Long): Boolean {
        return sharedPreferences.contains(getSelectedProjectsKey(orderId))
    }

    /**
     * 生成选中项目的存储键
     * @param orderId 订单ID
     * @return 存储键
     */
    private fun getSelectedProjectsKey(orderId: Long): String {
        return "${KEY_SELECTED_PROJECTS}_$orderId"
    }
}