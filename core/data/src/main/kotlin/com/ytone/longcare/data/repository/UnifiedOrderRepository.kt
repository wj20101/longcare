package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderElderInfoEntity
import com.ytone.longcare.model.OrderEntity
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.OrderProjectEntity
import com.ytone.longcare.model.ServiceOrderInfoModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class UnifiedOrderRepository @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus,
    private val runtimeConfigProvider: RuntimeConfigProvider,
    private val orderDao: OrderDao,
    private val orderElderInfoDao: OrderElderInfoDao,
    private val orderLocalStateDao: OrderLocalStateDao,
    private val orderProjectDao: OrderProjectDao
) : OrderDetailRepository {
    private val memoryCache = OrderInfoMemoryCache()
    private val roomSyncDelegate = OrderRoomSyncDelegate(
        orderDao = orderDao,
        orderElderInfoDao = orderElderInfoDao,
        orderLocalStateDao = orderLocalStateDao,
        orderProjectDao = orderProjectDao
    )

    override suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean): ApiResult<ServiceOrderInfoModel> {
        if (!forceRefresh) {
            memoryCache.get(orderKey)?.let { return ApiResult.Success(it) }
        }
        return memoryCache.withOrderLock(orderKey) {
            if (!forceRefresh) {
                memoryCache.get(orderKey)?.let { return@withOrderLock ApiResult.Success(it) }
            }
            val apiResult = safeApiCall(ioDispatcher, eventBus) {
                apiService.getOrderInfo(OrderInfoParamModel(orderKey.orderId))
            }
            if (apiResult is ApiResult.Success) {
                logOrderIdConsistencyIfDebug(
                    source = "getOrderInfo",
                    requestOrderId = orderKey.orderId,
                    payloadOrderId = apiResult.data.orderId
                )
                memoryCache.put(orderKey, apiResult.data)
                roomSyncDelegate.syncOrderInfoToRoom(orderKey.orderId, apiResult.data)
            }
            apiResult
        }
    }

    override fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? = memoryCache.get(orderKey)

    fun updateCachedOrderInfo(orderKey: OrderKey, orderInfo: ServiceOrderInfoModel) {
        memoryCache.put(orderKey, orderInfo)
    }

    override fun clearOrderInfoCache(orderKey: OrderKey) {
        memoryCache.remove(orderKey)
    }

    fun clearAllOrderInfoCache() {
        memoryCache.clear()
    }

    override suspend fun preloadOrderInfo(orderKey: OrderKey) {
        if (memoryCache.get(orderKey) == null) {
            getOrderInfo(orderKey, forceRefresh = false)
        }
    }

    fun observeOrderWithDetails(orderKey: OrderKey): Flow<OrderWithDetails?> {
        val orderId = orderKey.orderId
        return combine(
            orderDao.observeOrderById(orderId),
            orderElderInfoDao.observeByOrderId(orderId),
            orderLocalStateDao.observeByOrderId(orderId),
            orderProjectDao.observeProjectsByOrderId(orderId)
        ) { order, elderInfo, localState, projects ->
            order?.let {
                OrderWithDetails(
                    order = it.toModel(),
                    elderInfo = elderInfo?.toModel(),
                    localState = localState?.toModel(),
                    projects = projects.map { item -> item.toModel() }
                )
            }
        }
    }

    fun observeSelectedProjects(orderKey: OrderKey): Flow<List<OrderProjectEntity>> {
        return orderProjectDao.observeSelectedProjects(orderKey.orderId).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getOrderDetails(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<OrderWithDetails> {
        if (!forceRefresh) {
            roomSyncDelegate.loadOrderWithDetails(orderKey.orderId)?.let { return ApiResult.Success(it) }
        }
        return refreshOrderFromApi(orderKey)
    }

    suspend fun refreshOrderFromApi(orderKey: OrderKey): ApiResult<OrderWithDetails> {
        val orderId = orderKey.orderId
        val apiResult = safeApiCall(ioDispatcher, eventBus) {
            apiService.getOrderInfo(OrderInfoParamModel(orderId))
        }
        return when (apiResult) {
            is ApiResult.Success -> {
                logOrderIdConsistencyIfDebug(
                    source = "refreshOrderFromApi",
                    requestOrderId = orderId,
                    payloadOrderId = apiResult.data.orderId
                )
                ApiResult.Success(
                    roomSyncDelegate.persistOrderInfoAndBuildDetails(orderId, apiResult.data)
                )
            }
            is ApiResult.Failure -> apiResult
            is ApiResult.Exception -> apiResult
        }
    }

    suspend fun updateProjectSelection(orderKey: OrderKey, projectId: Int, isSelected: Boolean) {
        val orderId = orderKey.orderId
        orderProjectDao.updateSelection(orderId, projectId, isSelected)
        orderLocalStateDao.updateNeedsSync(orderId, true)
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

    suspend fun deleteOrder(orderKey: OrderKey) {
        orderDao.deleteById(orderKey.orderId)
    }

    suspend fun clearAllOrders() {
        orderDao.deleteAll()
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

data class OrderWithDetails(
    val order: OrderEntity,
    val elderInfo: OrderElderInfoEntity?,
    val localState: OrderLocalStateEntity?,
    val projects: List<OrderProjectEntity>
)
