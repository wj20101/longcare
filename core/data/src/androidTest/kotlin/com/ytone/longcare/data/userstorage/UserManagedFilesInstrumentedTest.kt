package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserManagedFilesInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registry = UserStorageRegistry(
        pathsFactory = pathsFactory,
        metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
        dataStoreRegistry = UserDataStoreRegistry(dataStoreScope),
        databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
    )
    private val files = UserManagedFiles(context, pathsFactory, registry, Dispatchers.IO)
    private val scopeA = UserScopeKey(7101, 7201, 7301)
    private val scopeB = UserScopeKey(7102, 7202, 7302)

    @Before
    @After
    fun cleanUp() = runBlocking {
        registry.close()
        listOf(scopeA, scopeB).forEach { scope ->
            val paths = pathsFactory.forScope(scope)
            context.deleteDatabase(paths.databaseFile.name)
            paths.dataStoreFile.delete()
            paths.namespaceRoot.deleteRecursively()
            paths.sessionRoot.parentFile?.deleteRecursively()
        }
    }

    @After
    fun stopDataStoreScope() {
        dataStoreScope.cancel()
    }

    @Test
    fun sameOrderAndFilenameRemainPhysicalIsolatedAcrossReopenAndCleanup() = runBlocking {
        val leaseA1 = registry.open(scopeA, SessionEpoch(1))
        val persistentA = files.persistentFile(leaseA1, "order_images", "orders/77/same.jpg").write("A")
        val sessionA = files.sessionFile(leaseA1, "photo_upload", "same.jpg").write("A-temp")
        val handleA = files.persistentHandle(leaseA1, "order_images", persistentA)

        val leaseB1 = registry.open(scopeB, SessionEpoch(2))
        val persistentB = files.persistentFile(leaseB1, "order_images", "orders/77/same.jpg").write("B")
        val sessionB = files.sessionFile(leaseB1, "photo_upload", "same.jpg").write("B-temp")
        val handleB = files.persistentHandle(leaseB1, "order_images", persistentB)

        assertNotEquals(persistentA.canonicalPath, persistentB.canonicalPath)
        assertNotEquals(sessionA.canonicalPath, sessionB.canonicalPath)
        assertEquals("B", files.resolvePersistentFile(leaseB1, handleB).readText())
        assertTrue(
            runCatching { files.sessionFile(leaseB1, "photo_upload", "../escape.jpg") }.isFailure
        )

        val leaseA2 = registry.open(scopeA, SessionEpoch(3))
        assertEquals("A", files.resolvePersistentFile(leaseA2, handleA).readText())
        assertEquals("A-temp", files.sessionFile(leaseA2, "photo_upload", "same.jpg").readText())
        files.deletePersistentFiles(leaseA2, listOf(handleA))
        files.clearSessionPurpose(leaseA2, "photo_upload")

        assertFalse(persistentA.exists())
        assertFalse(sessionA.exists())
        assertTrue(persistentB.exists())
        assertTrue(sessionB.exists())
        val leaseB2 = registry.open(scopeB, SessionEpoch(4))
        assertEquals("B", files.resolvePersistentFile(leaseB2, handleB).readText())
        assertEquals("B-temp", files.sessionFile(leaseB2, "photo_upload", "same.jpg").readText())
    }

    private fun java.io.File.write(contents: String) = apply {
        parentFile?.mkdirs()
        writeText(contents)
    }
}
