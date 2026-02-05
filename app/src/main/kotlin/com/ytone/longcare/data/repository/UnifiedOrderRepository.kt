package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.OrderInfoParamModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.entity.OrderElderInfoEntity
import com.ytone.longcare.data.database.entity.OrderEntity
import com.ytone.longcare.data.database.entity.OrderLocalStateEntity
import com.ytone.longcare.data.database.entity.OrderProjectEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderElderInfoEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderProjectEntities
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import com.ytone.longcare.model.OrderKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一订单仓库
 * 
 * 职责：
 * 1. 管理API调用和数据库同步
 * 2. 提供Flow供UI订阅（Single Source of Truth）
 * 3. 管理本地状态（选中项目、人脸验证等）
 */
@Singleton
class UnifiedOrderRepository @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus,
    private val orderDao: OrderDao,
    private val orderElderInfoDao: OrderElderInfoDao,
    private val orderLocalStateDao: OrderLocalStateDao,
    private val orderProjectDao: OrderProjectDao
) {
    
    // ========== 内存缓存（ServiceOrderInfoModel）==========
    
    // 缓存的订单详情数据（线程安全，使用OrderKey.cacheKey作为Key）
    private val _cachedOrderInfo = ConcurrentHashMap<String, ServiceOrderInfoModel>()
    
    // 每个订单Key对应的加载锁，使用Mutex确保同一订单不会并发加载
    private val orderLoadMutexes = ConcurrentHashMap<String, Mutex>()
    
    // 获取指定OrderKey的Mutex（线程安全，懒创建）
    private fun getOrderMutex(orderKey: OrderKey): Mutex {
        return orderLoadMutexes.getOrPut(orderKey.cacheKey) { Mutex() }
    }
    
    // ========== ServiceOrderInfoModel 获取（兼容SharedOrderRepository）==========
    
    /**
     * 获取订单详情（原始API模型）
     * 如果缓存中存在则直接返回，否则从网络获取并缓存
     * 使用Mutex确保同一订单的并发请求只会发起一次网络调用
     * @param orderKey 订单标识符
     * @param forceRefresh 是否强制刷新
     * @return 订单详情结果
     */
    suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<ServiceOrderInfoModel> {
        val cacheKey = orderKey.cacheKey
        
        // 快速路径：如果不强制刷新且缓存中存在数据，直接返回缓存
        if (!forceRefresh) {
            _cachedOrderInfo[cacheKey]?.let { cached ->
                return ApiResult.Success(cached)
            }
        }
        
        // 获取该订单的Mutex，确保并发请求串行化
        val mutex = getOrderMutex(orderKey)
        
        return mutex.withLock {
            // 双重检查：在获取锁后再次检查缓存（可能其他协程已加载完成）
            if (!forceRefresh) {
                _cachedOrderInfo[cacheKey]?.let { cached ->
                    return@withLock ApiResult.Success(cached)
                }
            }
            
            // 从网络获取数据
            val apiResult = safeApiCall(ioDispatcher, eventBus) { 
                apiService.getOrderInfo(OrderInfoParamModel(orderKey.orderId)) 
            }
            
            when (apiResult) {
                is ApiResult.Success -> {
                    // 更新内存缓存
                    _cachedOrderInfo[cacheKey] = apiResult.data
                    
                    // 同时同步到Room（保持一致性）
                    syncOrderInfoToRoom(orderKey.orderId, apiResult.data)
                }
                else -> {
                    // 网络请求失败，不更新缓存
                }
            }
            
            apiResult
        }
    }
    
    /**
     * 获取缓存的订单详情（线程安全）
     * @param orderKey 订单标识符
     * @return 缓存的订单详情，如果不存在则返回null
     */
    fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? {
        return _cachedOrderInfo[orderKey.cacheKey]
    }
    
    /**
     * 更新缓存的订单详情（线程安全）
     * @param orderKey 订单标识符
     * @param orderInfo 订单详情
     */
    fun updateCachedOrderInfo(orderKey: OrderKey, orderInfo: ServiceOrderInfoModel) {
        _cachedOrderInfo[orderKey.cacheKey] = orderInfo
    }
    
    /**
     * 清除指定订单的缓存（线程安全）
     * @param orderKey 订单标识符
     */
    fun clearOrderInfoCache(orderKey: OrderKey) {
        _cachedOrderInfo.remove(orderKey.cacheKey)
        orderLoadMutexes.remove(orderKey.cacheKey)
    }
    
    /**
     * 清除所有订单缓存（线程安全）
     */
    fun clearAllOrderInfoCache() {
        _cachedOrderInfo.clear()
        orderLoadMutexes.clear()
    }
    
    /**
     * 预加载订单详情（线程安全）
     * @param orderKey 订单标识符
     */
    suspend fun preloadOrderInfo(orderKey: OrderKey) {
        if (!_cachedOrderInfo.containsKey(orderKey.cacheKey)) {
            getOrderInfo(orderKey)
        }
    }
    
    /**
     * 同步订单信息到Room
     */
    private suspend fun syncOrderInfoToRoom(orderId: Long, orderInfo: ServiceOrderInfoModel) {
        // 转换并保存订单核心信息
        val orderEntity = orderInfo.toOrderEntity()
        orderDao.insertOrUpdate(orderEntity)
        
        // 转换并保存老人信息
        val elderInfoEntity = orderInfo.userInfo?.toOrderElderInfoEntity(orderId)
        if (elderInfoEntity != null) {
            orderElderInfoDao.insertOrUpdate(elderInfoEntity)
        }
        
        // 转换并保存项目列表
        val projectEntities = orderInfo.projectList?.toOrderProjectEntities(orderId) ?: emptyList()
        if (projectEntities.isNotEmpty()) {
            // 保留现有的选中状态
            val existingSelections = orderProjectDao.getSelectedProjectIds(orderId).toSet()
            val updatedProjects = projectEntities.map { project ->
                project.copy(isSelected = existingSelections.contains(project.projectId))
            }
            orderProjectDao.deleteByOrderId(orderId)
            orderProjectDao.insertOrUpdateAll(updatedProjects)
        }
        
        // 确保本地状态存在
        if (orderLocalStateDao.getByOrderId(orderId) == null) {
            orderLocalStateDao.insertOrUpdate(OrderLocalStateEntity(orderId = orderId))
        }
    }
    
    // ========== 订单数据获取 ==========
    
    /**
     * 观察订单完整数据
     * 返回订单核心信息、老人信息、本地状态和项目列表的组合Flow
     * @param orderKey 订单标识符
     */
    fun observeOrderWithDetails(orderKey: OrderKey): Flow<OrderWithDetails?> {
        val orderId = orderKey.orderId
        return combine(
            orderDao.observeOrderById(orderId),
            orderElderInfoDao.observeByOrderId(orderId),
            orderLocalStateDao.observeByOrderId(orderId),
            orderProjectDao.observeProjectsByOrderId(orderId)
        ) { order, elderInfo, localState, projects ->
            if (order != null) {
                OrderWithDetails(
                    order = order,
                    elderInfo = elderInfo,
                    localState = localState,
                    projects = projects
                )
            } else {
                null
            }
        }
    }
    
    /**
     * 观察订单的选中项目
     * @param orderKey 订单标识符
     */
    fun observeSelectedProjects(orderKey: OrderKey): Flow<List<OrderProjectEntity>> {
        return orderProjectDao.observeSelectedProjects(orderKey.orderId)
    }
    
    /**
     * 获取订单详情（优先从数据库，无数据时从API获取）
     * @param orderKey 订单标识符
     * @param forceRefresh 是否强制刷新
     */
    suspend fun getOrderDetails(orderKey: OrderKey, forceRefresh: Boolean = false): ApiResult<OrderWithDetails> {
        val orderId = orderKey.orderId
        // 如果不强制刷新，先尝试从数据库获取
        if (!forceRefresh) {
            val cachedOrder = orderDao.getOrderById(orderId)
            if (cachedOrder != null) {
                val elderInfo = orderElderInfoDao.getByOrderId(orderId)
                val localState = orderLocalStateDao.getByOrderId(orderId)
                val projects = orderProjectDao.getProjectsByOrderId(orderId)
                return ApiResult.Success(
                    OrderWithDetails(
                        order = cachedOrder,
                        elderInfo = elderInfo,
                        localState = localState,
                        projects = projects
                    )
                )
            }
        }
        
        // 从API获取并同步到数据库
        return refreshOrderFromApi(orderKey)
    }
    
    /**
     * 从API刷新订单数据并同步到数据库
     * @param orderKey 订单标识符
     */
    suspend fun refreshOrderFromApi(orderKey: OrderKey): ApiResult<OrderWithDetails> {
        val orderId = orderKey.orderId
        val apiResult = safeApiCall(ioDispatcher, eventBus) { 
            apiService.getOrderInfo(OrderInfoParamModel(orderId)) 
        }
        
        return when (apiResult) {
            is ApiResult.Success -> {
                val orderInfo = apiResult.data
                
                // 转换并保存订单核心信息
                val orderEntity = orderInfo.toOrderEntity()
                orderDao.insertOrUpdate(orderEntity)
                
                // 转换并保存老人信息
                val elderInfoEntity = orderInfo.userInfo?.toOrderElderInfoEntity(orderId)
                if (elderInfoEntity != null) {
                    orderElderInfoDao.insertOrUpdate(elderInfoEntity)
                }
                
                // 转换并保存项目列表
                val projectEntities = orderInfo.projectList?.toOrderProjectEntities(orderId) ?: emptyList()
                if (projectEntities.isNotEmpty()) {
                    // 保留现有的选中状态
                    val existingSelections = orderProjectDao.getSelectedProjectIds(orderId).toSet()
                    val updatedProjects = projectEntities.map { project ->
                        project.copy(isSelected = existingSelections.contains(project.projectId))
                    }
                    orderProjectDao.deleteByOrderId(orderId) // 先删除旧的
                    orderProjectDao.insertOrUpdateAll(updatedProjects)
                }
                
                // 确保本地状态存在
                val localState = orderLocalStateDao.getByOrderId(orderId)
                    ?: OrderLocalStateEntity(orderId = orderId).also {
                        orderLocalStateDao.insertOrUpdate(it)
                    }
                
                ApiResult.Success(
                    OrderWithDetails(
                        order = orderEntity,
                        elderInfo = elderInfoEntity,
                        localState = localState,
                        projects = orderProjectDao.getProjectsByOrderId(orderId)
                    )
                )
            }
            is ApiResult.Failure -> apiResult
            is ApiResult.Exception -> apiResult
        }
    }
    
    // ========== 项目选择操作 ==========
    
    /**
     * 更新项目选中状态
     * @param orderKey 订单标识符
     * @param projectId 项目ID
     * @param isSelected 是否选中
     */
    suspend fun updateProjectSelection(orderKey: OrderKey, projectId: Int, isSelected: Boolean) {
        val orderId = orderKey.orderId
        orderProjectDao.updateSelection(orderId, projectId, isSelected)
        // 标记需要同步
        orderLocalStateDao.updateNeedsSync(orderId, true)
    }
    
    /**
     * 批量更新选中的项目
     * @param orderKey 订单标识符
     * @param selectedProjectIds 选中的项目ID列表
     */
    suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>) {
        val orderId = orderKey.orderId
        orderProjectDao.updateSelectedProjects(orderId, selectedProjectIds)
        orderLocalStateDao.updateNeedsSync(orderId, true)
    }
    
    /**
     * 获取选中的项目ID列表
     * @param orderKey 订单标识符
     */
    suspend fun getSelectedProjectIds(orderKey: OrderKey): List<Int> {
        return orderProjectDao.getSelectedProjectIds(orderKey.orderId)
    }
    
    // ========== 本地状态操作 ==========
    
    /**
     * 开始服务（更新本地状态）
     * @param orderKey 订单标识符
     */
    suspend fun startLocalService(orderKey: OrderKey) {
        val orderId = orderKey.orderId
        val now = System.currentTimeMillis()
        // 确保本地状态存在
        if (orderLocalStateDao.getByOrderId(orderId) == null) {
            orderLocalStateDao.insertOrUpdate(OrderLocalStateEntity(orderId = orderId))
        }
        orderLocalStateDao.startService(orderId, now)
    }
    
    /**
     * 结束服务（更新本地状态）
     * @param orderKey 订单标识符
     */
    suspend fun endLocalService(orderKey: OrderKey) {
        val orderId = orderKey.orderId
        val now = System.currentTimeMillis()
        orderLocalStateDao.endService(orderId, now)
    }
    
    /**
     * 更新人脸验证状态
     * @param orderKey 订单标识符
     * @param completed 是否完成
     */
    suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean) {
        orderLocalStateDao.updateFaceVerification(orderKey.orderId, completed)
    }
    
    /**
     * 获取本地状态
     * @param orderKey 订单标识符
     */
    suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity? {
        return orderLocalStateDao.getByOrderId(orderKey.orderId)
    }
    
    // ========== 清理操作 ==========
    
    /**
     * 删除订单所有相关数据
     * @param orderKey 订单标识符
     */
    suspend fun deleteOrder(orderKey: OrderKey) {
        orderDao.deleteById(orderKey.orderId) // 外键级联删除会自动清理关联表
    }
    
    /**
     * 清空所有订单数据
     */
    suspend fun clearAllOrders() {
        orderDao.deleteAll()
    }
}

/**
 * 订单完整数据组合
 */
data class OrderWithDetails(
    val order: OrderEntity,
    val elderInfo: OrderElderInfoEntity?,
    val localState: OrderLocalStateEntity?,
    val projects: List<OrderProjectEntity>
)
