package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class UnifiedOrderRepository @Inject constructor(
    private val apiService: LongCareApiService,
    private val runtimeConfigProvider: RuntimeConfigProvider,
    private val databaseAccess: UserDatabaseAccess,
) : OrderDetailRepository, SessionRuntimeCleanupHook {
    private val memoryCache = OrderInfoMemoryCache()
    private val roomSyncDelegate = OrderRoomSyncDelegate(databaseAccess)

    override suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean): ApiResult<ServiceOrderInfoModel> {
        val lease = databaseAccess.currentLease()
        if (!forceRefresh) {
            memoryCache.get(lease, orderKey)?.let { return ApiResult.Success(it) }
        }
        return memoryCache.withOrderLock(lease, orderKey) {
            databaseAccess.requireValid(lease)
            if (!forceRefresh) {
                memoryCache.get(lease, orderKey)?.let { return@withOrderLock ApiResult.Success(it) }
            }
            val apiResult =
                apiService.getOrderInfo(OrderInfoParamModel(orderKey.orderId))
            if (apiResult is ApiResult.Success) {
                logOrderIdConsistencyIfDebug(
                    source = "getOrderInfo",
                    requestOrderId = orderKey.orderId,
                    payloadOrderId = apiResult.data.orderId
                )
                databaseAccess.requireValid(lease)
                memoryCache.put(lease, orderKey, apiResult.data)
                roomSyncDelegate.syncOrderInfoToRoom(lease, orderKey.orderId, apiResult.data)
            }
            apiResult
        }
    }

    override fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? =
        runCatching { memoryCache.get(databaseAccess.currentLease(), orderKey) }.getOrNull()

    fun updateCachedOrderInfo(orderKey: OrderKey, orderInfo: ServiceOrderInfoModel) {
        val lease = databaseAccess.currentLease()
        databaseAccess.requireValid(lease)
        memoryCache.put(lease, orderKey, orderInfo)
    }

    override fun clearOrderInfoCache(orderKey: OrderKey) {
        runCatching { memoryCache.remove(databaseAccess.currentLease(), orderKey) }
    }

    fun clearAllOrderInfoCache() {
        memoryCache.clear()
    }

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        memoryCache.clear()
    }

    override suspend fun preloadOrderInfo(orderKey: OrderKey) {
        val lease = databaseAccess.currentLease()
        if (memoryCache.get(lease, orderKey) == null) {
            getOrderInfo(orderKey, forceRefresh = false)
        }
    }

    fun observeOrderWithDetails(orderKey: OrderKey): Flow<OrderWithDetails?> {
        val orderId = orderKey.orderId
        return databaseAccess.observeCurrent { database, _ ->
            combine(
                database.orderDao().observeOrderById(orderId),
                database.orderElderInfoDao().observeByOrderId(orderId),
                database.orderLocalStateDao().observeByOrderId(orderId),
                database.orderProjectDao().observeProjectsByOrderId(orderId),
            ) { order, elderInfo, localState, projects ->
                order?.let {
                    OrderWithDetails(
                        order = it.toModel(),
                        elderInfo = elderInfo?.toModel(),
                        localState = localState?.toModel(),
                        projects = projects.map { item -> item.toModel() },
                    )
                }
            }
        }
    }

    fun observeSelectedProjects(orderKey: OrderKey): Flow<List<OrderProjectEntity>> {
        return databaseAccess.observeCurrent { database, _ ->
            database.orderProjectDao().observeSelectedProjects(orderKey.orderId).map { list ->
                list.map { it.toModel() }
            }
        }
    }

    suspend fun getOrderDetails(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<OrderWithDetails> {
        val lease = databaseAccess.currentLease()
        if (!forceRefresh) {
            roomSyncDelegate.loadOrderWithDetails(lease, orderKey.orderId)?.let { return ApiResult.Success(it) }
        }
        return refreshOrderFromApi(lease, orderKey)
    }

    suspend fun refreshOrderFromApi(orderKey: OrderKey): ApiResult<OrderWithDetails> {
        return refreshOrderFromApi(databaseAccess.currentLease(), orderKey)
    }

    private suspend fun refreshOrderFromApi(
        lease: UserStorageLease,
        orderKey: OrderKey,
    ): ApiResult<OrderWithDetails> {
        val orderId = orderKey.orderId
        val apiResult = apiService.getOrderInfo(OrderInfoParamModel(orderId))
        return when (apiResult) {
            is ApiResult.Success -> {
                logOrderIdConsistencyIfDebug(
                    source = "refreshOrderFromApi",
                    requestOrderId = orderId,
                    payloadOrderId = apiResult.data.orderId
                )
                ApiResult.Success(
                    roomSyncDelegate.persistOrderInfoAndBuildDetails(lease, orderId, apiResult.data)
                )
            }
            is ApiResult.Failure -> apiResult
            is ApiResult.Exception -> apiResult
        }
    }

    suspend fun updateProjectSelection(orderKey: OrderKey, projectId: Int, isSelected: Boolean) {
        val orderId = orderKey.orderId
        databaseAccess.withCurrentLease { database, _ ->
            database.orderProjectDao().updateSelection(orderId, projectId, isSelected)
            database.orderLocalStateDao().updateNeedsSync(orderId, true)
        }
    }

    override suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>) {
        val orderId = orderKey.orderId
        databaseAccess.withCurrentLease { database, _ ->
            database.orderProjectDao().updateSelectedProjects(orderId, selectedProjectIds)
            database.orderLocalStateDao().updateNeedsSync(orderId, true)
        }
    }

    override suspend fun getSelectedProjectIds(orderKey: OrderKey): List<Int> {
        return databaseAccess.withCurrentLease { database, _ ->
            database.orderProjectDao().getSelectedProjectIds(orderKey.orderId)
        }
    }

    override suspend fun startLocalService(orderKey: OrderKey) {
        val orderId = orderKey.orderId
        databaseAccess.withCurrentLease { database, _ ->
            if (database.orderLocalStateDao().getByOrderId(orderId) == null) {
                database.orderLocalStateDao().insertOrUpdate(OrderLocalStateEntity(orderId = orderId).toDb())
            }
            database.orderLocalStateDao().startService(orderId, System.currentTimeMillis())
        }
    }

    override suspend fun endLocalService(orderKey: OrderKey) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderLocalStateDao().endService(orderKey.orderId, System.currentTimeMillis())
        }
    }

    override suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderLocalStateDao().updateFaceVerification(orderKey.orderId, completed)
        }
    }

    override suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity? {
        return databaseAccess.withCurrentLease { database, _ ->
            database.orderLocalStateDao().getByOrderId(orderKey.orderId)?.toModel()
        }
    }

    suspend fun deleteOrder(orderKey: OrderKey) {
        databaseAccess.withCurrentLease { database, _ ->
            database.orderDao().deleteById(orderKey.orderId)
        }
    }

    suspend fun clearAllOrders() {
        databaseAccess.withCurrentLease { database, _ -> database.orderDao().deleteAll() }
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
