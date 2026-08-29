package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate
import com.ytone.longcare.domain.userstorage.UserStorageState
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserStorageRegistryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val touchedScopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUpFiles() {
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
    fun `namespace cannot open before destructive cutover gate completes`() = runTest {
        val gate = BlockingCutoverGate()
        val scope = trackedScope(399, 499, 599)
        val fixture = fixture(backgroundScope, gate)

        val opening = async(Dispatchers.Default) {
            fixture.registry.open(scope, SessionEpoch(999))
        }
        gate.started.await()

        assertEquals(UserStorageState.LoggedOut, fixture.registry.state.value)
        assertFalse(pathsFactory.forScope(scope).databaseFile.exists())
        assertFalse(pathsFactory.forScope(scope).namespaceRoot.exists())

        gate.release.complete(Unit)
        val lease = opening.await()

        assertEquals(lease, fixture.registry.requireCurrentLease())
        fixture.registry.close()
    }

    @Test
    fun `concurrent open reuses lease and switching closes database and rejects stale lease`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = trackedScope(401, 501, 601)
        val scopeB = trackedScope(402, 502, 602)

        val leases = List(24) {
            async(Dispatchers.Default) { fixture.registry.open(scopeA, SessionEpoch(1001)) }
        }.awaitAll()
        val leaseA = leases.first()
        assertTrue(leases.all { it == leaseA })
        val databaseA = fixture.registry.database(leaseA)
        val dataStoreA = fixture.registry.dataStore(leaseA)

        val leaseB = fixture.registry.open(scopeB, SessionEpoch(1002))

        assertFalse(databaseA.isOpen)
        assertTrue(fixture.registry.state.value is UserStorageState.Ready)
        assertEquals(leaseB, fixture.registry.requireCurrentLease())
        assertTrue(runCatching { fixture.registry.database(leaseA) }.exceptionOrNull() is UserStorageUnavailableException)
        assertTrue(runCatching { fixture.registry.dataStore(leaseA) }.exceptionOrNull() is UserStorageUnavailableException)

        val reloginA = fixture.registry.open(scopeA, SessionEpoch(1003))
        assertNotEquals(leaseA.generation, reloginA.generation)
        assertSame(dataStoreA, fixture.registry.dataStore(reloginA))
        fixture.registry.close()
        assertEquals(UserStorageState.LoggedOut, fixture.registry.state.value)
    }

    @Test
    fun `managed session cleanup is lease bound and never deletes persistent or another user files`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = trackedScope(411, 511, 611)
        val scopeB = trackedScope(412, 512, 612)
        val leaseA1 = fixture.registry.open(scopeA, SessionEpoch(2001))
        val persistentA = fixture.files.persistentFile(leaseA1, "images", "same/photo.jpg").write("persistent-a")
        val sessionAUpload = fixture.files.sessionFile(leaseA1, "uploads", "same/photo.jpg").write("upload-a")
        val sessionAFace = fixture.files.sessionFile(leaseA1, "face", "same/photo.jpg").write("face-a")

        val leaseB = fixture.registry.open(scopeB, SessionEpoch(2002))
        val sessionB = fixture.files.sessionFile(leaseB, "uploads", "same/photo.jpg").write("upload-b")
        assertTrue(runCatching { fixture.files.clearAllSessionFiles(leaseA1) }.exceptionOrNull() is UserStorageUnavailableException)

        val leaseA2 = fixture.registry.open(scopeA, SessionEpoch(2003))
        fixture.files.clearSessionPurpose(leaseA2, "uploads")
        assertFalse(sessionAUpload.exists())
        assertTrue(sessionAFace.exists())
        assertTrue(persistentA.exists())
        assertTrue(sessionB.exists())

        fixture.files.clearAllSessionFiles(leaseA2)
        assertFalse(sessionAFace.exists())
        assertTrue(persistentA.exists())
        assertTrue(sessionB.exists())
        fixture.registry.close()
    }

    private fun fixture(
        applicationScope: CoroutineScope,
        cutoverGate: UserStorageNamespaceCutoverGate? = null,
    ): Fixture {
        val dataStoreRegistry = UserDataStoreRegistry(applicationScope)
        val databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory)
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = dataStoreRegistry,
            databaseFactory = databaseFactory,
            cutoverGate = cutoverGate ?: com.ytone.longcare.data.startup.AssumedCompletedCutoverGate,
        )
        return Fixture(
            registry = registry,
            files = UserManagedFiles(context, pathsFactory, registry, Dispatchers.IO),
        )
    }

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(touchedScopes::add)

    private fun java.io.File.write(contents: String): java.io.File = apply {
        parentFile?.mkdirs()
        writeText(contents)
    }

    private data class Fixture(
        val registry: UserStorageRegistry,
        val files: UserManagedFiles,
    )

    private class BlockingCutoverGate : UserStorageNamespaceCutoverGate {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        @Volatile
        override var isCompleted: Boolean = false
            private set

        override suspend fun ensureCompleted() {
            started.complete(Unit)
            release.await()
            isCompleted = true
        }
    }
}
