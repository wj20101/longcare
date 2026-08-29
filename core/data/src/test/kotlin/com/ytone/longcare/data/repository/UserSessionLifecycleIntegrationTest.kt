package com.ytone.longcare.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.data.session.SessionEnvelope
import com.ytone.longcare.data.session.SessionEnvelopePersistence
import com.ytone.longcare.data.session.SessionEnvelopeReadResult
import com.ytone.longcare.data.userstorage.ManagedSessionFilesCleanupHook
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserManagedFiles
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.data.userstorage.UserStorageUnavailableException
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageState
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class UserSessionLifecycleIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val touchedScopes = mutableSetOf<UserScopeKey>()
    private val devicePreferences by lazy {
        context.getSharedPreferences("session_lifecycle_device_sentinel", Context.MODE_PRIVATE)
    }

    @After
    fun cleanUp() {
        devicePreferences.edit().clear().commit()
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
    fun `manual logout revokes room stops runtime and deletes only session files`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(
            applicationScope = backgroundScope,
            events = events,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val scope = trackedScope(71, 72, 73)
        fixture.repository.sessionState.first { it is SessionState.LoggedOut }
        fixture.repository.login(payload(scope))
        val lease = fixture.registry.requireCurrentLease()
        val database = fixture.registry.database(lease)
        val temporary = fixture.files.sessionFile(lease, "photo_upload", "order/capture.jpg").write("temp")
        val persistent = fixture.files.persistentFile(lease, "order_images", "order/photo.jpg").write("keep")
        devicePreferences.edit().putString("install_guid", "device-guid").commit()
        events.clear()

        fixture.repository.logout()

        assertEquals(listOf("revoke", "stop:${scope.namespaceId().value}", "close"), events)
        assertEquals(SessionState.LoggedOut, fixture.repository.sessionState.value)
        assertEquals(UserStorageState.LoggedOut, fixture.registry.state.value)
        assertFalse(database.isOpen)
        assertFalse(temporary.exists())
        assertTrue(persistent.exists())
        assertEquals("device-guid", devicePreferences.getString("install_guid", null))
        assertTrue(fixture.persistence.cleared)
        assertNull(fixture.repository.requestAuthSnapshot())
        assertTrue(
            runCatching { fixture.registry.database(lease) }.exceptionOrNull() is
                UserStorageUnavailableException
        )
    }

    @Test
    fun `token invalidation uses same lifecycle and preserves device preferences`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(
            applicationScope = backgroundScope,
            events = events,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val handler = DefaultSessionInvalidationHandler(fixture.repository, backgroundScope)
        val scope = trackedScope(81, 82, 83)
        fixture.repository.sessionState.first { it is SessionState.LoggedOut }
        fixture.repository.login(payload(scope))
        val lease = fixture.registry.requireCurrentLease()
        val database = fixture.registry.database(lease)
        val temporary = fixture.files.sessionFile(lease, "face", "capture.jpg").write("secret-face")
        devicePreferences.edit().putBoolean("privacy_runtime_flag", true).commit()
        events.clear()
        runCurrent()

        handler.invalidate("token expired")
        runCurrent()
        withTimeout(5_000) {
            fixture.repository.sessionState.first { it is SessionState.LoggedOut }
        }
        withTimeout(5_000) {
            handler.invalidations.first { it != null }
        }

        assertEquals(listOf("revoke", "stop:${scope.namespaceId().value}", "close"), events)
        assertFalse(database.isOpen)
        assertFalse(temporary.exists())
        assertTrue(devicePreferences.getBoolean("privacy_runtime_flag", false))
        assertTrue(fixture.persistence.cleared)
        assertNull(fixture.repository.requestAuthSnapshot())
        assertTrue(
            runCatching { fixture.registry.requireCurrentLease() }.exceptionOrNull() is
                UserStorageUnavailableException
        )
    }

    private fun fixture(
        applicationScope: CoroutineScope,
        events: MutableList<String>,
        ioDispatcher: CoroutineDispatcher,
    ): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, ioDispatcher, pathsFactory),
        )
        val files = UserManagedFiles(context, pathsFactory, registry, ioDispatcher)
        val persistence = FakePersistence()
        val runtime = object : SessionStorageRuntime {
            override suspend fun open(
                scopeKey: UserScopeKey,
                sessionEpoch: SessionEpoch,
            ): UserStorageLease = registry.open(scopeKey, sessionEpoch)

            override suspend fun revoke() {
                events += "revoke"
                registry.revoke()
            }

            override suspend fun close() {
                events += "close"
                registry.close()
            }
        }
        val stopHook = SessionRuntimeCleanupHook { identity ->
            check(registry.state.value is UserStorageState.Closing)
            events += "stop:${identity.scopeKey.namespaceId().value}"
        }
        val managedFilesHook = ManagedSessionFilesCleanupHook(files)
        val repository = DefaultUserSessionRepository(
            persistence = persistence,
            storageRuntime = runtime,
            applicationScope = applicationScope,
            epochSource = IncreasingEpochSource(),
            cleanupHooks = { linkedSetOf(stopHook, managedFilesHook) },
        )
        return Fixture(repository, registry, files, persistence)
    }

    private fun payload(scope: UserScopeKey) = SessionLoginPayload(
        companyId = scope.companyId,
        accountId = scope.accountId,
        userId = scope.userId,
        userName = "user-${scope.userId}",
        headUrl = "",
        userIdentity = 1,
        identityCardNumber = "330000199901011234",
        gender = 0,
        token = "session-secret",
    )

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(touchedScopes::add)

    private fun java.io.File.write(value: String) = apply {
        parentFile?.mkdirs()
        writeText(value)
    }

    private data class Fixture(
        val repository: DefaultUserSessionRepository,
        val registry: UserStorageRegistry,
        val files: UserManagedFiles,
        val persistence: FakePersistence,
    )

    private class FakePersistence : SessionEnvelopePersistence {
        private var envelope: SessionEnvelope? = null
        var cleared = false
            private set

        override suspend fun read(): SessionEnvelopeReadResult = envelope
            ?.let(SessionEnvelopeReadResult::Loaded)
            ?: SessionEnvelopeReadResult.Missing

        override suspend fun write(envelope: SessionEnvelope) {
            this.envelope = envelope
            cleared = false
        }

        override suspend fun clear() {
            envelope = null
            cleared = true
        }
    }

    private class IncreasingEpochSource : SessionEpochSource {
        private var epoch = 9_000L

        override fun observe(epoch: SessionEpoch) {
            this.epoch = maxOf(this.epoch, epoch.value)
        }

        override fun next(): SessionEpoch = SessionEpoch(++epoch)
    }
}
