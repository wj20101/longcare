package com.ytone.longcare.common.utils

import androidx.navigation.NavController
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.navigation.navigateToServiceCountdown
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航辅助工具类
 * 封装常用的导航逻辑，确保跳转行为的一致性
 */
@Singleton
class NavigationHelper @Inject constructor(
    private val selectedProjectsManager: SelectedProjectsManager
) {
    
    /**
     * 统一的跳转到服务倒计时页面的逻辑
     * @param navController 导航控制器
     * @param orderId 订单ID
     * @param projectList 所有项目列表，用于全选时的默认值
     * @param selectedProjectIds 指定的选中项目ID列表，如果为null则从本地存储获取
     */
    fun navigateToServiceCountdownWithLogic(
        navController: NavController,
        orderId: Long,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>? = null
    ) {
        // 确定最终使用的项目ID列表
        val finalProjectIds = selectedProjectIds ?: run {
            // 从本地存储获取选中的项目ID
            val savedProjectIds = selectedProjectsManager.getSelectedProjects(orderId)
            
            // 如果本地存储为空，则默认为全选
            if (savedProjectIds?.isEmpty() != false) {
                projectList.map { it.projectId }
            } else {
                savedProjectIds
            }
        }
        
        // 执行导航
        navController.navigateToServiceCountdown(OrderInfoRequestModel(orderId = orderId, planId = 0), finalProjectIds)
    }
    
    /**
     * 从本地存储获取选中的项目ID，如果为空则返回全选
     * @param orderId 订单ID
     * @param projectList 所有项目列表
     * @return 选中的项目ID列表
     */
    fun getSelectedProjectIdsOrDefault(
        orderId: Long,
        projectList: List<ServiceProjectM>
    ): List<Int> {
        val savedProjectIds = selectedProjectsManager.getSelectedProjects(orderId)
        return if (savedProjectIds?.isEmpty() != false) {
            projectList.map { it.projectId }
        } else {
            savedProjectIds
        }
    }
}