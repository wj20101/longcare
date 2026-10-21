package com.ytone.longcare.domain.repository

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel

interface OrderDetailRepository {
    suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<ServiceOrderInfoModel>
    fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel?
    suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>)
    suspend fun getSelectedProjectIds(orderKey: OrderKey): List<Int>
    /** 保存首次确认的服务起点；重复准备不能延长倒计时。 */
    suspend fun startLocalService(orderKey: OrderKey, startedAtMillis: Long)
    suspend fun endLocalService(orderKey: OrderKey)
    suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean)
    suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity?
}
