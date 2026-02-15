package com.ytone.longcare.domain.repository

import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.data.database.entity.OrderLocalStateEntity
import com.ytone.longcare.model.OrderKey

interface OrderDetailRepository {
    suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<ServiceOrderInfoModel>
    fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel?
    suspend fun preloadOrderInfo(orderKey: OrderKey)
    suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>)
    suspend fun getSelectedProjectIds(orderKey: OrderKey): List<Int>
    fun clearOrderInfoCache(orderKey: OrderKey)
    suspend fun startLocalService(orderKey: OrderKey)
    suspend fun endLocalService(orderKey: OrderKey)
    suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean)
    suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity?
}
