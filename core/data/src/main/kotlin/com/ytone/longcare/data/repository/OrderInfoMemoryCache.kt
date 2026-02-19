package com.ytone.longcare.data.repository

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OrderInfoMemoryCache {
    private val cachedOrderInfo = ConcurrentHashMap<String, ServiceOrderInfoModel>()
    private val orderLoadMutexes = ConcurrentHashMap<String, Mutex>()

    fun get(orderKey: OrderKey): ServiceOrderInfoModel? {
        return cachedOrderInfo[orderKey.cacheKey]
    }

    fun put(orderKey: OrderKey, orderInfo: ServiceOrderInfoModel) {
        cachedOrderInfo[orderKey.cacheKey] = orderInfo
    }

    fun remove(orderKey: OrderKey) {
        val cacheKey = orderKey.cacheKey
        cachedOrderInfo.remove(cacheKey)
        orderLoadMutexes.remove(cacheKey)
    }

    fun clear() {
        cachedOrderInfo.clear()
        orderLoadMutexes.clear()
    }

    suspend fun <T> withOrderLock(orderKey: OrderKey, block: suspend () -> T): T {
        val mutex = orderLoadMutexes.getOrPut(orderKey.cacheKey) { Mutex() }
        return mutex.withLock { block() }
    }
}
