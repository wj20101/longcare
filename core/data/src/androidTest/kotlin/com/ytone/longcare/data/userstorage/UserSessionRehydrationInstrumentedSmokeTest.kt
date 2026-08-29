package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.data.repository.DefaultUserSessionRepository
import com.ytone.longcare.data.repository.SessionEpochSource
import com.ytone.longcare.data.repository.SessionStorageRuntime
import com.ytone.longcare.data.session.AndroidKeystoreSessionCipher
import com.ytone.longcare.data.session.EncryptedSessionEnvelopeStore
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeReadyHook
import com.ytone.longcare.domain.userstorage.UserRehydrationState
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.UserScopeKey
import com.ytone.longcare.model.result.ApiResult
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserSessionRehydrationInstrumentedSmokeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val infrastructureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionScopes = mutableListOf<CoroutineScope>()
    private val touchedScopes = mutableSetOf<UserScopeKey>()
    private val sessionFile = File(
        context.noBackupFilesDir,
        "session/instrumented_user_storage_smoke.preferences_pb",
    )
    private var registry: UserStorageRegistry? = null

    @After
    fun tearDown() = runBlocking {
        runCatching { registry?.close() }
        sessionScopes.forEach(CoroutineScope::cancel)
        infrastructureScope.cancel()
        sessionFile.delete()
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
    fun loginARehydrateLogoutLoginBSameOrderAndRestoreRemainPhysicallyIsolated() = runBlocking {
        sessionFile.parentFile?.mkdirs()
        sessionFile.delete()
        val processSessionDataStore = PreferenceDataStoreFactory.create(scope = infrastructureScope) {
            sessionFile
        }
        val persistence = EncryptedSessionEnvelopeStore(
            processSessionDataStore,
            AndroidKeystoreSessionCipher(),
        )
        val server = FakeRehydrationServer()
        val activeRegistry = newRegistry().also { registry = it }
        val files = UserManagedFiles(context, pathsFactory, activeRegistry, Dispatchers.IO)
        val scopeA = scope(companyId = 31_001, accountId = 31_002, userId = 31_003)
        val scopeB = scope(companyId = 41_001, accountId = 41_002, userId = 41_003)

        val firstProcess = newSessionHarness(activeRegistry, persistence, server)
        withTimeout(TIMEOUT_MILLIS) {
            firstProcess.repository.sessionState.first { it is SessionState.LoggedOut }
        }

        server.snapshot.set(ServerSnapshot.forUser("A", SAME_ORDER_ID))
        firstProcess.repository.login(payload(scopeA, "token-A"))
        awaitReady(firstProcess, scopeA)
        val leaseA = activeRegistry.requireCurrentLease()
        val snapshotA = firstProcess.orderStore.getAll(leaseA).single()
        val fileA = files.persistentFile(
            leaseA,
            purpose = "smoke_orders",
            relativePath = "$SAME_ORDER_ID/cache.json",
        ).write("A")
        assertEquals("A-company", firstProcess.configManager.getSystemConfig()?.companyName)
        assertEquals("A-elder", snapshotA.elderName)

        firstProcess.repository.logout()
        assertTrue(
            runCatching { activeRegistry.database(leaseA) }.exceptionOrNull() is
                UserStorageUnavailableException,
        )

        server.snapshot.set(ServerSnapshot.forUser("B", SAME_ORDER_ID))
        firstProcess.repository.login(payload(scopeB, "token-B"))
        awaitReady(firstProcess, scopeB)
        val leaseB = activeRegistry.requireCurrentLease()
        val snapshotB = firstProcess.orderStore.getAll(leaseB).single()
        val fileB = files.persistentFile(
            leaseB,
            purpose = "smoke_orders",
            relativePath = "$SAME_ORDER_ID/cache.json",
        ).write("B")

        assertEquals("B-company", firstProcess.configManager.getSystemConfig()?.companyName)
        assertEquals("B-elder", snapshotB.elderName)
        assertNotEquals(fileA.absolutePath, fileB.absolutePath)
        assertEquals("A", fileA.readText())
        assertEquals("B", fileB.readText())
        assertNotEquals(pathsFactory.forScope(scopeA).databaseFile, pathsFactory.forScope(scopeB).databaseFile)
        assertTrue(pathsFactory.forScope(scopeA).databaseFile.exists())
        assertTrue(pathsFactory.forScope(scopeB).databaseFile.exists())
        assertTrue(pathsFactory.forScope(scopeA).dataStoreFile.exists())
        assertTrue(pathsFactory.forScope(scopeB).dataStoreFile.exists())

        val encryptedBytes = sessionFile.readBytes()
        assertFalse(encryptedBytes.containsUtf8("token-B"))
        assertFalse(encryptedBytes.containsUtf8(scopeB.userId.toString()))

        // Model process death: discard the first session scope and Room handle while retaining
        // the encrypted ACTIVE envelope and process-unique DataStore instance.
        firstProcess.scope.cancel()
        activeRegistry.close()
        val recreatedProcess = newSessionHarness(activeRegistry, persistence, server)
        withTimeout(TIMEOUT_MILLIS) {
            recreatedProcess.repository.sessionState.first { state ->
                state is SessionState.LoggedIn && state.user.scopeKey == scopeB
            }
        }
        awaitReady(recreatedProcess, scopeB)
        val restoredLease = activeRegistry.requireCurrentLease()
        assertEquals(scopeB, restoredLease.scopeKey)
        assertEquals("B-company", recreatedProcess.configManager.getSystemConfig()?.companyName)
        assertEquals("B-elder", recreatedProcess.orderStore.getAll(restoredLease).single().elderName)
        assertEquals("A", fileA.readText())
        assertEquals("B", fileB.readText())

        recreatedProcess.repository.logout()
    }

    private fun newRegistry(): UserStorageRegistry = UserStorageRegistry(
        pathsFactory = pathsFactory,
        metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
        dataStoreRegistry = UserDataStoreRegistry(infrastructureScope),
        databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
    )

    private fun newSessionHarness(
        storageRegistry: UserStorageRegistry,
        persistence: EncryptedSessionEnvelopeStore,
        server: FakeRehydrationServer,
    ): SessionHarness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also(sessionScopes::add)
        val databaseAccess = UserDatabaseAccess(storageRegistry)
        val configManager = SystemConfigManager(
            applicationScope = scope,
            moshi = Moshi.Builder().build(),
            apiService = server.api,
            storageRegistry = storageRegistry,
        )
        val orderStore = InitialOrderSnapshotStore(databaseAccess)
        val coordinator = DefaultUserRehydrationCoordinator(
            apiService = server.api,
            systemConfigManager = configManager,
            databaseAccess = databaseAccess,
            orderSnapshotStore = orderStore,
        )
        val runtime = object : SessionStorageRuntime {
            override suspend fun open(
                scopeKey: UserScopeKey,
                sessionEpoch: SessionEpoch,
            ): UserStorageLease = storageRegistry.open(scopeKey, sessionEpoch)

            override suspend fun revoke() = storageRegistry.revoke()

            override suspend fun close() = storageRegistry.close()
        }
        val repository = DefaultUserSessionRepository(
            persistence = persistence,
            storageRuntime = runtime,
            applicationScope = scope,
            epochSource = IncreasingEpochSource(),
            cleanupHooks = {
                linkedSetOf<SessionRuntimeCleanupHook>(coordinator, configManager)
            },
            readyHooks = {
                linkedSetOf<SessionRuntimeReadyHook>(coordinator)
            },
        )
        return SessionHarness(scope, repository, coordinator, configManager, orderStore)
    }

    private suspend fun awaitReady(harness: SessionHarness, expectedScope: UserScopeKey) {
        val ready = withTimeout(TIMEOUT_MILLIS) {
            harness.coordinator.state.first { state ->
                state is UserRehydrationState.Ready &&
                    state.identity.namespaceId == expectedScope.namespaceId()
            }
        } as UserRehydrationState.Ready
        assertEquals(1, ready.todayOrderCount)
        assertEquals(0, ready.inProgressOrderCount)
    }

    private fun payload(scope: UserScopeKey, token: String) = SessionLoginPayload(
        companyId = scope.companyId,
        accountId = scope.accountId,
        userId = scope.userId,
        userName = "user-${scope.userId}",
        headUrl = "",
        userIdentity = 1,
        identityCardNumber = "330000199901011234",
        gender = 0,
        token = token,
    )

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(touchedScopes::add)

    private fun File.write(value: String): File = apply {
        parentFile?.mkdirs()
        writeText(value)
    }

    private fun ByteArray.containsUtf8(value: String): Boolean =
        indexOfSubsequence(value.encodeToByteArray()) >= 0

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        for (start in 0..size - needle.size) {
            if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) return start
        }
        return -1
    }

    private data class SessionHarness(
        val scope: CoroutineScope,
        val repository: DefaultUserSessionRepository,
        val coordinator: DefaultUserRehydrationCoordinator,
        val configManager: SystemConfigManager,
        val orderStore: InitialOrderSnapshotStore,
    )

    private data class ServerSnapshot(
        val config: SystemConfigModel,
        val todayOrders: List<TodayServiceOrderModel>,
        val inProgressOrders: List<ServiceOrderModel>,
    ) {
        companion object {
            fun forUser(label: String, orderId: Long) = ServerSnapshot(
                config = SystemConfigModel(companyName = "$label-company"),
                todayOrders = listOf(
                    TodayServiceOrderModel(
                        orderId = orderId,
                        userId = if (label == "A") 101 else 202,
                        name = "$label-elder",
                        liveAddress = "$label-address",
                    ),
                ),
                inProgressOrders = emptyList(),
            )
        }
    }

    private class FakeRehydrationServer {
        val snapshot = AtomicReference(ServerSnapshot.forUser("initial", SAME_ORDER_ID))

        val api: LongCareApiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getSystemConfig" -> ApiResult.Success(snapshot.get().config)
                "getTodayOrderList" -> ApiResult.Success(snapshot.get().todayOrders)
                "getInOrderList" -> ApiResult.Success(snapshot.get().inProgressOrders)
                "toString" -> "FakeRehydrationServer"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> error("Unexpected API call during rehydration smoke: ${method.name}")
            }
        } as LongCareApiService
    }

    private class IncreasingEpochSource : SessionEpochSource {
        private var value = 50_000L

        override fun observe(epoch: SessionEpoch) {
            value = maxOf(value, epoch.value)
        }

        override fun next(): SessionEpoch = SessionEpoch(++value)
    }

    private companion object {
        const val SAME_ORDER_ID = 9_001L
        const val TIMEOUT_MILLIS = 20_000L
    }
}
