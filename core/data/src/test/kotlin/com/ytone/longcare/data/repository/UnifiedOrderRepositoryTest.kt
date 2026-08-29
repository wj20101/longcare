package com.ytone.longcare.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UnifiedOrderRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUp() {
        scopes.forEach { scope ->
            pathsFactory.forScope(scope).also { paths ->
                context.deleteDatabase(paths.databaseFile.name)
                paths.dataStoreFile.delete()
                paths.namespaceRoot.deleteRecursively()
                paths.sessionRoot.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun `same order id and memory cache remain isolated across account switch`() = runTest {
        val api = mockk<LongCareApiService>()
        val fixture = fixture(backgroundScope, api)
        val scopeA = trackedScope(1001, 2001, 3001)
        val scopeB = trackedScope(1002, 2002, 3002)
        val orderKey = OrderKey(12345L, 7)
        val responseA = order(orderKey.orderId, state = 11, projectId = 101)
        val responseB = order(orderKey.orderId, state = 22, projectId = 202)
        coEvery { api.getOrderInfo(any()) } returnsMany listOf(
            ApiResult.Success(responseA),
            ApiResult.Success(responseB),
        )

        val leaseA = fixture.registry.open(scopeA, SessionEpoch(1))
        assertEquals(responseA, (fixture.repository.getOrderInfo(orderKey, false) as ApiResult.Success).data)
        assertEquals(11, fixture.registry.database(leaseA).orderDao().getOrderById(orderKey.orderId)?.state)

        val leaseB = fixture.registry.open(scopeB, SessionEpoch(2))
        assertEquals(responseB, (fixture.repository.getOrderInfo(orderKey, false) as ApiResult.Success).data)
        assertEquals(22, fixture.registry.database(leaseB).orderDao().getOrderById(orderKey.orderId)?.state)

        fixture.registry.open(scopeA, SessionEpoch(3))
        val reopenedA = fixture.repository.getOrderDetails(orderKey, forceRefresh = false)
        assertTrue(reopenedA is ApiResult.Success)
        assertEquals(11, (reopenedA as ApiResult.Success).data.order.state)
        assertEquals(listOf(101), reopenedA.data.projects.map { it.projectId })
        coVerify(exactly = 2) { api.getOrderInfo(any()) }
        fixture.registry.close()
    }

    @Test
    fun `order observation stops old database and switches to new user`() = runTest {
        val fixture = fixture(backgroundScope, mockk(relaxed = true))
        val scopeA = trackedScope(1011, 2011, 3011)
        val scopeB = trackedScope(1012, 2012, 3012)
        val orderId = 88L
        val values = mutableListOf<Int>()
        val firstValue = CompletableDeferred<Unit>()
        val secondValue = CompletableDeferred<Unit>()
        fixture.registry.open(scopeA, SessionEpoch(11))
        fixture.databaseAccess.withCurrentLease { database, _ ->
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = orderId, state = 1))
        }
        val collection = backgroundScope.launch(Dispatchers.IO) {
            fixture.repository.observeOrderWithDetails(OrderKey(orderId))
                .filterNotNull()
                .map { it.order.state }
                .onEach { state ->
                    if (state == 1) firstValue.complete(Unit)
                    if (state == 2) secondValue.complete(Unit)
                }
                .take(2)
                .toList(values)
        }
        awaitReal(firstValue)
        fixture.registry.open(scopeB, SessionEpoch(12))
        fixture.databaseAccess.withCurrentLease { database, _ ->
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = orderId, state = 2))
        }
        awaitReal(secondValue)
        awaitReal(collection)

        assertEquals(listOf(1, 2), values)
        assertTrue(collection.isCompleted)
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

    private fun order(orderId: Long, state: Int, projectId: Int) = ServiceOrderInfoModel(
        orderId = orderId,
        state = state,
        projectList = listOf(ServiceProjectM(projectId = projectId, projectName = "project-$projectId")),
    )

    private suspend fun awaitReal(signal: CompletableDeferred<Unit>) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { signal.await() }
        }
    }

    private suspend fun awaitReal(job: Job) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { job.join() }
        }
    }

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val databaseAccess: UserDatabaseAccess,
        val repository: UnifiedOrderRepository,
    )
}
