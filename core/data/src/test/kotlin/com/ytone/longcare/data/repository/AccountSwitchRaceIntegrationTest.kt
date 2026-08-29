package com.ytone.longcare.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.session.SessionOperationTracker
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.data.userstorage.UserStorageUnavailableException
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountSwitchRaceIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val touchedScopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUp() {
        touchedScopes.forEach { scope ->
            pathsFactory.forScope(scope).also { paths ->
                context.deleteDatabase(paths.databaseFile.name)
                paths.dataStoreFile.delete()
                paths.namespaceRoot.deleteRecursively()
                paths.sessionRoot.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun `late A database network and upload callbacks cannot affect ready B`() = runTest {
        val scopeA = trackedScope(91_001, 91_002, 91_003)
        val scopeB = trackedScope(92_001, 92_002, 92_003)
        val orderKey = OrderKey(orderId = 44_001, planId = 7)
        val responseA = ServiceOrderInfoModel(orderId = orderKey.orderId, state = 11)
        val responseB = ServiceOrderInfoModel(orderId = orderKey.orderId, state = 22)
        val networkStarted = CompletableDeferred<Unit>()
        val releaseNetworkA = CompletableDeferred<Unit>()
        val releaseDatabaseA = CompletableDeferred<Unit>()
        val uploadContinuation = CompletableDeferred<CancellableContinuation<Unit>>()
        val uploadCommitted = AtomicBoolean(false)
        val activeUploadSession = AtomicReference<String?>(null)
        val api = mockk<LongCareApiService>()
        coEvery { api.getOrderInfo(any()) } coAnswers {
            networkStarted.complete(Unit)
            releaseNetworkA.await()
            ApiResult.Success(responseA)
        }
        val fixture = fixture(backgroundScope, api)
        val uploadTracker = SessionOperationTracker()
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(101))
        val uploadSessionA = "${scopeA.namespaceId().value}:101"
        activeUploadSession.set(uploadSessionA)

        val delayedNetworkA = backgroundScope.async(Dispatchers.IO) {
            runCatching { fixture.repository.getOrderInfo(orderKey, forceRefresh = true) }
        }
        awaitReal(networkStarted)

        val delayedDatabaseA = backgroundScope.async(Dispatchers.IO) {
            releaseDatabaseA.await()
            runCatching {
                fixture.databaseAccess.withLease(leaseA) { database, _ ->
                    database.orderDao().insertOrUpdate(
                        OrderEntityDb(orderId = orderKey.orderId, state = 99),
                    )
                }
            }
        }
        val delayedUploadA = backgroundScope.async(Dispatchers.IO) {
            uploadTracker.track(
                sessionFingerprint = uploadSessionA,
                validateSession = {
                    check(activeUploadSession.get() == uploadSessionA) {
                        "Upload belongs to an expired session"
                    }
                },
            ) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    uploadContinuation.complete(continuation)
                }
                fixture.databaseAccess.withLease(leaseA) { database, _ ->
                    database.orderDao().insertOrUpdate(
                        OrderEntityDb(orderId = orderKey.orderId, state = 77),
                    )
                }
                uploadCommitted.set(true)
            }
        }
        val capturedUploadCallback = awaitReal(uploadContinuation)

        // Production lifecycle revokes and joins upload work before B can become Ready.
        activeUploadSession.set(null)
        uploadTracker.cancelAndJoin(uploadSessionA)
        fixture.registry.open(scopeB, SessionEpoch(202))
        activeUploadSession.set("${scopeB.namespaceId().value}:202")
        fixture.databaseAccess.withCurrentLease { database, _ ->
            database.orderDao().insertOrUpdate(
                OrderEntityDb(orderId = orderKey.orderId, state = responseB.state),
            )
        }
        fixture.repository.updateCachedOrderInfo(orderKey, responseB)

        // All three A callbacks attempt to return only after B is Ready.
        val uploadCallbackAccepted = capturedUploadCallback.isActive.also { accepted ->
            if (accepted) capturedUploadCallback.resumeWith(Result.success(Unit))
        }
        releaseDatabaseA.complete(Unit)
        releaseNetworkA.complete(Unit)

        val databaseResult = awaitReal(delayedDatabaseA)
        val networkResult = awaitReal(delayedNetworkA)
        joinReal(delayedUploadA)

        assertFalse(uploadCallbackAccepted)
        assertTrue(delayedUploadA.isCancelled)
        assertFalse(uploadCommitted.get())
        assertTrue(databaseResult.exceptionOrNull() is UserStorageUnavailableException)
        assertTrue(networkResult.exceptionOrNull() is UserStorageUnavailableException)
        assertEquals(responseB, fixture.repository.getCachedOrderInfo(orderKey))
        assertEquals(
            responseB.state,
            fixture.databaseAccess.withCurrentLease { database, _ ->
                database.orderDao().getOrderById(orderKey.orderId)?.state
            },
        )

        val reopenedA = fixture.registry.open(scopeA, SessionEpoch(303))
        assertNull(fixture.registry.database(reopenedA).orderDao().getOrderById(orderKey.orderId))
        fixture.registry.close()
    }

    private fun fixture(
        applicationScope: kotlinx.coroutines.CoroutineScope,
        api: LongCareApiService,
    ): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val databaseAccess = UserDatabaseAccess(registry)
        val runtimeConfig = mockk<RuntimeConfigProvider>().also {
            every { it.isDebug } returns false
        }
        return Fixture(
            registry = registry,
            databaseAccess = databaseAccess,
            repository = UnifiedOrderRepository(api, runtimeConfig, databaseAccess),
        )
    }

    private suspend fun <T> awaitReal(signal: CompletableDeferred<T>): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { signal.await() }
        }

    private suspend fun <T> awaitReal(job: kotlinx.coroutines.Deferred<T>): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { job.await() }
        }

    private suspend fun joinReal(job: Job) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { job.join() }
        }
    }

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(touchedScopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val databaseAccess: UserDatabaseAccess,
        val repository: UnifiedOrderRepository,
    )
}
