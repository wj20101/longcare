package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderInfoMemoryCacheTest {

    @Test
    fun `same order and plan never reuse values across namespace or generation`() {
        val cache = OrderInfoMemoryCache()
        val orderKey = OrderKey(orderId = 88, planId = 9)
        val leaseA = lease(companyId = 1, accountId = 2, userId = 3, epoch = 10, generation = 1)
        val leaseB = lease(companyId = 1, accountId = 2, userId = 4, epoch = 11, generation = 2)
        val valueA = ServiceOrderInfoModel(orderId = 88, state = 1)
        val valueB = ServiceOrderInfoModel(orderId = 88, state = 2)

        cache.put(leaseA, orderKey, valueA)
        assertEquals(valueA, cache.get(leaseA, orderKey))
        assertNull(cache.get(leaseB, orderKey))
        cache.put(leaseB, orderKey, valueB)

        assertEquals(valueB, cache.get(leaseB, orderKey))
        assertTrue(runCatching { cache.get(leaseA, orderKey) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `switch creates a new mutex and rejects old waiter`() = runTest {
        val cache = OrderInfoMemoryCache()
        val orderKey = OrderKey(orderId = 88, planId = 9)
        val leaseA = lease(companyId = 1, accountId = 2, userId = 3, epoch = 10, generation = 1)
        val leaseB = lease(companyId = 1, accountId = 2, userId = 4, epoch = 11, generation = 2)
        val enteredA = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()
        supervisorScope {
            val first = async {
                cache.withOrderLock(leaseA, orderKey) {
                    enteredA.complete(Unit)
                    releaseA.await()
                    "a"
                }
            }
            enteredA.await()
            val second = async { cache.withOrderLock(leaseB, orderKey) { "b" } }
            runCurrent()

            assertEquals("b", second.await())
            releaseA.complete(Unit)
            assertTrue(runCatching { first.await() }.exceptionOrNull() is IllegalStateException)
        }
        assertTrue(runCatching { cache.put(leaseA, orderKey, ServiceOrderInfoModel()) }.isFailure)
    }

    private fun lease(
        companyId: Int,
        accountId: Int,
        userId: Int,
        epoch: Long,
        generation: Long,
    ) = UserStorageLease(
        scopeKey = UserScopeKey(companyId, accountId, userId),
        sessionEpoch = SessionEpoch(epoch),
        generation = StorageGeneration(generation),
    )
}
