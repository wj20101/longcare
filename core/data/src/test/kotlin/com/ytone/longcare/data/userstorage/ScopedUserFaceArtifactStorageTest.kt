package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScopedUserFaceArtifactStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()
    private val recordKey = stringPreferencesKey(ScopedUserFaceArtifactStorage.FACE_CACHE_RECORD_KEY)

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
    fun `cleanup removes only active composite user record and face purpose`() = runTest {
        val fixture = fixture(backgroundScope)
        val a = scope(1, 2, 3)
        val b = scope(1, 9, 3)
        val leaseA1 = fixture.registry.open(a, SessionEpoch(10))
        fixture.registry.dataStore(leaseA1).edit { it[recordKey] = "a" }
        val fileA = fixture.files.sessionFile(leaseA1, "face", "capture.jpg").write("a")
        val leaseB = fixture.registry.open(b, SessionEpoch(11))
        fixture.registry.dataStore(leaseB).edit { it[recordKey] = "b" }
        val fileB = fixture.files.sessionFile(leaseB, "face", "capture.jpg").write("b")

        val leaseA2 = fixture.registry.open(a, SessionEpoch(12))
        fixture.storage.clearCurrentFaceArtifacts(expectedUserId = 3)

        assertEquals(null, fixture.registry.dataStore(leaseA2).data.first()[recordKey])
        assertFalse(fileA.exists())
        val leaseB2 = fixture.registry.open(b, SessionEpoch(13))
        assertEquals("b", fixture.registry.dataStore(leaseB2).data.first()[recordKey])
        assertTrue(fileB.exists())
    }

    @Test
    fun `mismatched user id fails closed without deleting current files`() = runTest {
        val fixture = fixture(backgroundScope)
        val lease = fixture.registry.open(scope(7, 8, 9), SessionEpoch(20))
        val faceFile = fixture.files.sessionFile(lease, "face", "capture.jpg").write("face")

        val failure = runCatching {
            fixture.storage.clearCurrentFaceArtifacts(expectedUserId = 10)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(faceFile.exists())
    }

    private fun fixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(scope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val files = UserManagedFiles(context, pathsFactory, registry, Dispatchers.IO)
        return Fixture(registry, files, ScopedUserFaceArtifactStorage(registry, files))
    }

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private fun java.io.File.write(value: String) = apply {
        parentFile?.mkdirs()
        writeText(value)
    }

    private data class Fixture(
        val registry: UserStorageRegistry,
        val files: UserManagedFiles,
        val storage: ScopedUserFaceArtifactStorage,
    )
}
