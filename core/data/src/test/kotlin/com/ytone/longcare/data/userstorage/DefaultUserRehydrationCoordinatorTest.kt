package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserRehydrationState
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUserRehydrationCoordinatorTest {
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
    fun `successful ready hook replaces stale values in the exact namespace`() = runTest {
        val api = successfulApi("fresh-config", todayOrderId = 101, inProgressOrderId = 202)
        val fixture = fixture(backgroundScope, api)
        val scope = scope(1, 2, 3)
        val lease = fixture.registry.open(scope, SessionEpoch(10))
        fixture.configManager.saveSystemConfig(SystemConfigModel(companyName = "stale-config"))
        fixture.orderStore.replace(
            lease,
            todayOrders = listOf(TodayServiceOrderModel(orderId = 999)),
            inProgressOrders = emptyList(),
        )

        fixture.coordinator.onReady(SessionRuntimeIdentity(scope, SessionEpoch(10)), lease)

        val state = fixture.coordinator.state.value
        assertTrue(state is UserRehydrationState.Ready)
        state as UserRehydrationState.Ready
        assertEquals(1, state.todayOrderCount)
        assertEquals(1, state.inProgressOrderCount)
        assertEquals("fresh-config", fixture.configManager.getSystemConfig()?.companyName)
        assertEquals(setOf(101L, 202L), fixture.orderStore.getAll(lease).map { it.orderId }.toSet())
        fixture.registry.close()
    }

    @Test
    fun `switch while requests are pending cancels A and only publishes B`() = runTest {
        val api = mockk<LongCareApiService>()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val configCalls = AtomicInteger()
        val todayCalls = AtomicInteger()
        val inProgressCalls = AtomicInteger()
        coEvery { api.getSystemConfig() } coAnswers {
            if (configCalls.getAndIncrement() == 0) {
                started.complete(Unit)
                gate.await()
                ApiResult.Success(SystemConfigModel(companyName = "A-must-not-commit"))
            } else {
                ApiResult.Success(SystemConfigModel(companyName = "B-config"))
            }
        }
        coEvery { api.getTodayOrderList() } coAnswers {
            if (todayCalls.getAndIncrement() == 0) {
                gate.await()
                ApiResult.Success(listOf(TodayServiceOrderModel(orderId = 111)))
            } else {
                ApiResult.Success(listOf(TodayServiceOrderModel(orderId = 222)))
            }
        }
        coEvery { api.getInOrderList() } coAnswers {
            if (inProgressCalls.getAndIncrement() == 0) {
                gate.await()
                ApiResult.Success(listOf(ServiceOrderModel(orderId = 111, planId = 1)))
            } else {
                ApiResult.Success(emptyList())
            }
        }
        val fixture = fixture(backgroundScope, api)
        val scopeA = scope(11, 12, 13)
        val scopeB = scope(11, 12, 14)
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(20))
        val workA = async {
            fixture.coordinator.onReady(SessionRuntimeIdentity(scopeA, SessionEpoch(20)), leaseA)
        }
        runCurrent()
        started.await()

        fixture.coordinator.cleanup(SessionRuntimeIdentity(scopeA, SessionEpoch(20)))
        assertTrue(workA.isCancelled)
        val leaseB = fixture.registry.open(scopeB, SessionEpoch(21))
        fixture.coordinator.onReady(SessionRuntimeIdentity(scopeB, SessionEpoch(21)), leaseB)

        val state = fixture.coordinator.state.value
        assertTrue(state is UserRehydrationState.Ready)
        state as UserRehydrationState.Ready
        assertEquals(scopeB.namespaceId(), state.identity.namespaceId)
        assertEquals("B-config", fixture.configManager.getSystemConfig()?.companyName)
        assertEquals(listOf(222L), fixture.orderStore.getAll(leaseB).map { it.orderId })
        assertTrue(pathsFactory.forScope(scopeA).databaseFile.exists())
        fixture.registry.close()
    }

    @Test
    fun `network failure clears stale snapshots and retry never falls back`() = runTest {
        val api = mockk<LongCareApiService>()
        coEvery { api.getSystemConfig() } returns ApiResult.Failure(503, "offline")
        coEvery { api.getTodayOrderList() } returns ApiResult.Failure(503, "offline")
        coEvery { api.getInOrderList() } returns ApiResult.Exception(IllegalStateException("offline"))
        val fixture = fixture(backgroundScope, api)
        val scope = scope(21, 22, 23)
        val lease = fixture.registry.open(scope, SessionEpoch(30))
        fixture.configManager.saveSystemConfig(SystemConfigModel(companyName = "stale"))
        fixture.orderStore.replace(
            lease,
            todayOrders = listOf(TodayServiceOrderModel(orderId = 999)),
            inProgressOrders = emptyList(),
        )

        fixture.coordinator.onReady(SessionRuntimeIdentity(scope, SessionEpoch(30)), lease)

        val failure = fixture.coordinator.state.value
        assertTrue(failure is UserRehydrationState.RetryableFailure)
        assertNull(fixture.configManager.getSystemConfig())
        assertFalse(fixture.configManager.hasSystemConfig())
        assertTrue(fixture.orderStore.getAll(lease).isEmpty())

        coEvery { api.getSystemConfig() } returns ApiResult.Success(
            SystemConfigModel(companyName = "retry-config")
        )
        coEvery { api.getTodayOrderList() } returns ApiResult.Success(
            listOf(TodayServiceOrderModel(orderId = 303))
        )
        coEvery { api.getInOrderList() } returns ApiResult.Success(emptyList())
        fixture.coordinator.retry()

        assertTrue(fixture.coordinator.state.value is UserRehydrationState.Ready)
        assertEquals("retry-config", fixture.configManager.getSystemConfig()?.companyName)
        assertEquals(listOf(303L), fixture.orderStore.getAll(lease).map { it.orderId })
        fixture.registry.close()
    }

    private fun successfulApi(
        companyName: String,
        todayOrderId: Long,
        inProgressOrderId: Long,
    ): LongCareApiService = mockk<LongCareApiService>().also { api ->
        coEvery { api.getSystemConfig() } returns ApiResult.Success(
            SystemConfigModel(companyName = companyName)
        )
        coEvery { api.getTodayOrderList() } returns ApiResult.Success(
            listOf(TodayServiceOrderModel(orderId = todayOrderId))
        )
        coEvery { api.getInOrderList() } returns ApiResult.Success(
            listOf(ServiceOrderModel(orderId = inProgressOrderId, planId = 7))
        )
    }

    private fun fixture(scope: CoroutineScope, api: LongCareApiService): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(scope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val databaseAccess = UserDatabaseAccess(registry)
        val configManager = SystemConfigManager(
            applicationScope = scope,
            moshi = Moshi.Builder().build(),
            apiService = api,
            storageRegistry = registry,
        )
        val orderStore = InitialOrderSnapshotStore(databaseAccess)
        return Fixture(
            registry = registry,
            configManager = configManager,
            orderStore = orderStore,
            coordinator = DefaultUserRehydrationCoordinator(
                apiService = api,
                systemConfigManager = configManager,
                databaseAccess = databaseAccess,
                orderSnapshotStore = orderStore,
            ),
        )
    }

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val configManager: SystemConfigManager,
        val orderStore: InitialOrderSnapshotStore,
        val coordinator: DefaultUserRehydrationCoordinator,
    )
}
