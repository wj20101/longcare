package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class UnifiedOrderRepository @Inject constructor(
    private val apiService: LongCareApiService,
    private val runtimeConfigProvider: RuntimeConfigProvider,
    private val orderDao: OrderDao,
    private val orderElderInfoDao: OrderElderInfoDao,
    private val orderLocalStateDao: OrderLocalStateDao,
    private val orderProjectDao: OrderProjectDao,
    private val session: UserSessionTracker,
) : OrderDetailRepository {
    private val memoryCache get() = session.orderInfo
    private val roomSyncDelegate = OrderRoomSyncDelegate(
        orderDao = orderDao,
        orderElderInfoDao = orderElderInfoDao,
        orderLocalStateDao = orderLocalStateDao,
        orderProjectDao = orderProjectDao
    )

    override suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean): ApiResult<ServiceOrderInfoModel> {
        val generation = session.sessionGeneration.value ?: throw CancellationException("No active user session")
        if (!forceRefresh) {
            memoryCache.get(orderKey)?.let { return ApiResult.Success(it) }
        }
        return memoryCache.withOrderLock(orderKey) {
            session.requireCurrent(generation)
            if (!forceRefresh) {
                memoryCache.get(orderKey)?.let { return@withOrderLock ApiResult.Success(it) }
            }
            val apiResult =
                apiService.getOrderInfo(OrderInfoParamModel(orderKey.orderId, orderKey.planId))
            session.requireCurrent(generation)
            if (apiResult is ApiResult.Success) {
                logOrderIdConsistencyIfDebug(
                    source = "getOrderInfo",
                    requestOrderId = orderKey.orderId,
                    payloadOrderId = apiResult.data.orderId
                )
                roomSyncDelegate.syncOrderInfoToRoom(orderKey.orderId, apiResult.data)
                memoryCache.put(orderKey, apiResult.data) { session.isCurrent(generation) }
            }
            apiResult
        }
    }

    override fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? =
        if (session.sessionGeneration.value != null) memoryCache.get(orderKey) else null

    override fun clearOrderInfoCache(orderKey: OrderKey) {
        memoryCache.remove(orderKey)
    }

    override suspend fun preloadOrderInfo(orderKey: OrderKey) {
        getOrderInfo(orderKey, forceRefresh = false)
    }

    override suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>) {
        val orderId = orderKey.orderId
        orderProjectDao.updateSelectedProjects(orderId, selectedProjectIds)
        orderLocalStateDao.updateNeedsSync(orderId, true)
    }

    override suspend fun getSelectedProjectIds(orderKey: OrderKey): List<Int> {
        return orderProjectDao.getSelectedProjectIds(orderKey.orderId)
    }

    override suspend fun startLocalService(orderKey: OrderKey) {
        val orderId = orderKey.orderId
        if (orderLocalStateDao.getByOrderId(orderId) == null) {
            orderLocalStateDao.insertOrUpdate(OrderLocalStateEntity(orderId = orderId).toDb())
        }
        orderLocalStateDao.startService(orderId, System.currentTimeMillis())
    }

    override suspend fun endLocalService(orderKey: OrderKey) {
        orderLocalStateDao.endService(orderKey.orderId, System.currentTimeMillis())
    }

    override suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean) {
        orderLocalStateDao.updateFaceVerification(orderKey.orderId, completed)
    }

    override suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity? {
        return orderLocalStateDao.getByOrderId(orderKey.orderId)?.toModel()
    }

    private fun logOrderIdConsistencyIfDebug(
        source: String,
        requestOrderId: Long,
        payloadOrderId: Long
    ) {
        if (!runtimeConfigProvider.isDebug) return
        val message = "OrderIdConsistency[$source]: requestOrderId=$requestOrderId, payloadOrderId=$payloadOrderId"
        if (payloadOrderId <= 0L || payloadOrderId != requestOrderId) {
            logE("$message, mismatch=true")
        } else {
            logI("$message, mismatch=false")
        }
    }
}
