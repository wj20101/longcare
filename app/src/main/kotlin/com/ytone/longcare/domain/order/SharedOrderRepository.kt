package com.ytone.longcare.domain.order

import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.network.ApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 共享订单详情仓库
 * 用于在多个页面间共享订单详情数据，避免重复网络请求
 */
@Singleton
class SharedOrderRepository @Inject constructor(
    private val orderRepository: OrderRepository
) {
    // 缓存的订单详情数据
    private val _cachedOrderInfo = MutableStateFlow<Map<Long, ServiceOrderInfoModel>>(emptyMap())
    val cachedOrderInfo: StateFlow<Map<Long, ServiceOrderInfoModel>> = _cachedOrderInfo.asStateFlow()
    
    // 当前正在加载的订单ID集合
    private val loadingOrderIds = mutableSetOf<Long>()
    
    /**
     * 获取订单详情
     * 如果缓存中存在则直接返回，否则从网络获取并缓存
     * @param orderId 订单ID
     * @param forceRefresh 是否强制刷新
     * @return 订单详情结果
     */
    suspend fun getOrderInfo(
        orderId: Long, 
        forceRefresh: Boolean = false
    ): ApiResult<ServiceOrderInfoModel> {
        // 如果不强制刷新且缓存中存在数据，直接返回缓存
        if (!forceRefresh && _cachedOrderInfo.value.containsKey(orderId)) {
            return ApiResult.Success(_cachedOrderInfo.value[orderId]!!)
        }
        
        // 如果正在加载中，等待加载完成
        if (loadingOrderIds.contains(orderId)) {
            // 等待加载完成后返回缓存数据
            while (loadingOrderIds.contains(orderId)) {
                delay(100)
            }
            return _cachedOrderInfo.value[orderId]?.let {
                ApiResult.Success(it)
            } ?: ApiResult.Failure(code = -1, message = "获取订单详情失败")
        }
        
        // 标记为正在加载
        loadingOrderIds.add(orderId)
        
        try {
            // 从网络获取数据
            val result = orderRepository.getOrderInfo(orderId)
            
            when (result) {
                is ApiResult.Success -> {
                    // 更新缓存
                    val currentCache = _cachedOrderInfo.value.toMutableMap()
                    currentCache[orderId] = result.data
                    _cachedOrderInfo.value = currentCache
                }
                else -> {
                    // 网络请求失败，不更新缓存
                }
            }
            
            return result
        } finally {
            // 移除加载标记
            loadingOrderIds.remove(orderId)
        }
    }
    
    /**
     * 获取缓存的订单详情
     * @param orderId 订单ID
     * @return 缓存的订单详情，如果不存在则返回null
     */
    fun getCachedOrderInfo(orderId: Long): ServiceOrderInfoModel? {
        return _cachedOrderInfo.value[orderId]
    }
    
    /**
     * 更新缓存的订单详情
     * @param orderId 订单ID
     * @param orderInfo 订单详情
     */
    fun updateCachedOrderInfo(orderId: Long, orderInfo: ServiceOrderInfoModel) {
        val currentCache = _cachedOrderInfo.value.toMutableMap()
        currentCache[orderId] = orderInfo
        _cachedOrderInfo.value = currentCache
    }
    
    /**
     * 清除指定订单的缓存
     * @param orderId 订单ID
     */
    fun clearOrderCache(orderId: Long) {
        val currentCache = _cachedOrderInfo.value.toMutableMap()
        currentCache.remove(orderId)
        _cachedOrderInfo.value = currentCache
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        _cachedOrderInfo.value = emptyMap()
        loadingOrderIds.clear()
    }
    
    /**
     * 预加载订单详情
     * 用于在用户可能访问某个页面之前提前加载数据
     * @param orderId 订单ID
     */
    suspend fun preloadOrderInfo(orderId: Long) {
        if (!_cachedOrderInfo.value.containsKey(orderId) && !loadingOrderIds.contains(orderId)) {
            getOrderInfo(orderId)
        }
    }
}