package com.ytone.longcare.data.userstorage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserManagedImageFileStoreTest {
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
    fun `logged out and switching storage reject creation and stale commit`() = runTest {
        val fixture = fixture(backgroundScope)
        assertTrue(
            runCatching { fixture.store.createSessionFile("photo_upload", "capture") }
                .exceptionOrNull() is UserStorageUnavailableException
        )
        val scopeA = scope(1, 2, 3)
        fixture.registry.open(scopeA, SessionEpoch(10))
        val fileA = fixture.store.createSessionFile("photo_upload", "capture")
        fileA.file.writeText("A")

        val scopeB = scope(1, 2, 4)
        fixture.registry.open(scopeB, SessionEpoch(11))
        val fileB = fixture.store.createSessionFile("photo_upload", "capture")
        fileB.file.writeText("B")

        assertNotEquals(fileA.file.canonicalPath, fileB.file.canonicalPath)
        assertTrue(runCatching { fixture.store.requireCurrent(fileA) }.isFailure)
        assertFalse(
            fixture.store.deleteCurrentSessionFile(
                Uri.fromFile(fileA.file),
                setOf("photo_upload"),
            )
        )
        assertTrue(fileA.file.exists())
        assertTrue(fileB.file.exists())
        assertTrue(fixture.store.deleteOwned(fileA))
        assertFalse(fileA.file.exists())
        assertTrue(fileB.file.exists())
    }

    @Test
    fun `upload source validation accepts only current session or persistent roots`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = scope(11, 12, 13)
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(20))
        val sessionA = fixture.store.createSessionFile("photo_upload", "session").file.apply {
            writeText("session-a")
        }
        fixture.registry.open(scope(11, 12, 14), SessionEpoch(21))

        assertTrue(
            runCatching { fixture.store.requireCurrentUserFile(Uri.fromFile(sessionA)) }
                .exceptionOrNull() is IllegalArgumentException
        )
        val leaseB = fixture.registry.requireCurrentLease()
        val persistentB = fixture.files.persistentFile(leaseB, "order_images", "orders/1/b.jpg").apply {
            parentFile?.mkdirs()
            writeText("persistent-b")
        }
        assertTrue(
            fixture.store.requireCurrentUserFile(Uri.fromFile(persistentB)).canonicalFile ==
                persistentB.canonicalFile
        )
        assertTrue(sessionA.exists())
        assertTrue(leaseA.scopeKey != leaseB.scopeKey)
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(scope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val files = UserManagedFiles(context, pathsFactory, registry, Dispatchers.IO)
        return Fixture(registry, files, UserManagedImageFileStore(registry, files))
    }

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val files: UserManagedFiles,
        val store: UserManagedImageFileStore,
    )
}
