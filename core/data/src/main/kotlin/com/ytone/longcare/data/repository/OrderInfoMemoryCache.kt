package com.ytone.longcare.data.repository

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.NamespaceId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OrderInfoMemoryCache {
    private data class CacheIdentity(
        val namespaceId: NamespaceId,
        val generation: StorageGeneration,
    )

    private val cachedOrderInfo = ConcurrentHashMap<String, ServiceOrderInfoModel>()
    private val orderLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val identityLock = Any()
    private var activeIdentity: CacheIdentity? = null

    fun get(lease: UserStorageLease, orderKey: OrderKey): ServiceOrderInfoModel? =
        synchronized(identityLock) {
            requireActiveLocked(lease)
            cachedOrderInfo[orderKey.cacheKey]
        }

    fun put(lease: UserStorageLease, orderKey: OrderKey, orderInfo: ServiceOrderInfoModel) {
        synchronized(identityLock) {
            requireActiveLocked(lease)
            cachedOrderInfo[orderKey.cacheKey] = orderInfo
        }
    }

    fun remove(lease: UserStorageLease, orderKey: OrderKey) {
        synchronized(identityLock) {
            requireActiveLocked(lease)
            val cacheKey = orderKey.cacheKey
            cachedOrderInfo.remove(cacheKey)
            orderLoadMutexes.remove(cacheKey)
        }
    }

    fun clear() {
        synchronized(identityLock) {
            cachedOrderInfo.clear()
            orderLoadMutexes.clear()
            activeIdentity = null
        }
    }

    suspend fun <T> withOrderLock(
        lease: UserStorageLease,
        orderKey: OrderKey,
        block: suspend () -> T,
    ): T {
        val mutex = synchronized(identityLock) {
            requireActiveLocked(lease)
            orderLoadMutexes.getOrPut(orderKey.cacheKey) { Mutex() }
        }
        return mutex.withLock {
            requireActive(lease)
            block().also { requireActive(lease) }
        }
    }

    private fun requireActive(lease: UserStorageLease) {
        synchronized(identityLock) {
            requireActiveLocked(lease)
        }
    }

    private fun requireActiveLocked(lease: UserStorageLease) {
        val requested = CacheIdentity(lease.scopeKey.namespaceId(), lease.generation)
        val current = activeIdentity
        when {
            current == null -> activeIdentity = requested
            current == requested -> Unit
            requested.generation.value > current.generation.value -> {
                cachedOrderInfo.clear()
                orderLoadMutexes.clear()
                activeIdentity = requested
            }
            else -> throw IllegalStateException("Stale user cache lease")
        }
    }
}
