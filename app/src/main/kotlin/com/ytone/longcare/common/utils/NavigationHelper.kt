package com.ytone.longcare.common.utils

import androidx.navigation.NavController
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.navigation.navigateToServiceCountdown
import com.ytone.longcare.navigation.OrderNavParams
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航辅助工具类
 * 封装常用的导航逻辑，确保跳转行为的一致性
 */
@Singleton
class NavigationHelper @Inject constructor(
    private val unifiedOrderRepository: UnifiedOrderRepository
) {
    
    /**
     * 统一的跳转到服务倒计时页面的逻辑
     * @param navController 导航控制器
     * @param orderParams 订单导航参数
     * @param projectList 所有项目列表，用于全选时的默认值
     * @param selectedProjectIds 指定的选中项目ID列表，如果为null则从Room获取
     */
    suspend fun navigateToServiceCountdownWithLogic(
        navController: NavController,
        orderParams: OrderNavParams,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>? = null
    ) {
        // 确定最终使用的项目ID列表
        val finalProjectIds = selectedProjectIds ?: run {
            // 从Room获取选中的项目ID
            val savedProjectIds = unifiedOrderRepository.getSelectedProjectIds(orderParams.toOrderKey())
            
            // 如果存储为空，则默认为全选
            if (savedProjectIds.isEmpty()) {
                projectList.map { it.projectId }
            } else {
                savedProjectIds
            }
        }
        
        // 执行导航
        navController.navigateToServiceCountdown(orderParams = orderParams, projectIdList = finalProjectIds)
    }
    
    /**
     * 从Room获取选中的项目ID，如果为空则返回全选
     * @param orderParams 订单导航参数
     * @param projectList 所有项目列表
     * @return 选中的项目ID列表
     */
    suspend fun getSelectedProjectIdsOrDefault(
        orderParams: OrderNavParams,
        projectList: List<ServiceProjectM>
    ): List<Int> {
        val savedProjectIds = unifiedOrderRepository.getSelectedProjectIds(orderParams.toOrderKey())
        return if (savedProjectIds.isEmpty()) {
            projectList.map { it.projectId }
        } else {
            savedProjectIds
        }
    }
}