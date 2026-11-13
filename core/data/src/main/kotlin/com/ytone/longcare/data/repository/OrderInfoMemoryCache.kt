package com.ytone.longcare.data.repository

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OrderInfoMemoryCache {
    private val stateLock = Any()
    private val cachedOrderInfo = ConcurrentHashMap<OrderKey, ServiceOrderInfoModel>()
    private val orderLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val revisions = mutableMapOf<Long, Long>()

    fun get(orderKey: OrderKey): ServiceOrderInfoModel? {
        return cachedOrderInfo[orderKey]
    }

    fun revision(orderId: Long): Long = synchronized(stateLock) { revisions[orderId] ?: 0L }

    fun put(orderKey: OrderKey, orderInfo: ServiceOrderInfoModel, revision: Long, isCurrent: () -> Boolean): Boolean = synchronized(stateLock) {
        if (!isCurrent()) throw CancellationException("User session changed")
        if ((revisions[orderKey.orderId] ?: 0L) != revision) return@synchronized false
        cachedOrderInfo[orderKey] = orderInfo
        true
    }

    fun invalidate(orderId: Long) {
        synchronized(stateLock) {
            revisions[orderId] = (revisions[orderId] ?: 0L) + 1L
            cachedOrderInfo.keys.removeAll { it.orderId == orderId }
            // Keep the existing load locks: replacing one would let old and new reads race.
        }
    }

    fun clear() = synchronized(stateLock) {
        cachedOrderInfo.clear()
        orderLoadMutexes.clear()
        revisions.clear()
    }

    suspend fun <T> withOrderLock(orderKey: OrderKey, block: suspend () -> T): T {
        val mutex = orderLoadMutexes.getOrPut(orderKey.cacheKey) { Mutex() }
        return mutex.withLock { block() }
    }
}
