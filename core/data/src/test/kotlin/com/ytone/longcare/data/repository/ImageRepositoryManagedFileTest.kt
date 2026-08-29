package com.ytone.longcare.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserManagedFiles
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageRepositoryManagedFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()
    private val sources = mutableSetOf<File>()

    @After
    fun cleanUp() {
        sources.forEach(File::delete)
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
    fun `database stores only relative handle and repository resolves current user file`() = runTest {
        val fixture = fixture(backgroundScope)
        val scope = trackedScope(901, 902, 903)
        val lease = fixture.registry.open(scope, SessionEpoch(1))
        fixture.registry.database(lease).orderDao().insertOrUpdate(OrderEntityDb(orderId = 42L))
        val source = source(fixture, lease, "source-a.jpg", "image-a")

        val imageId = fixture.repository.addImage(
            orderKey = OrderKey(42L),
            imageType = ImageType.BEFORE_CARE,
            localUri = Uri.fromFile(source).toString(),
            localPath = source.path,
        ).id

        val raw = requireNotNull(fixture.registry.database(lease).orderImageDao().getById(imageId))
        assertEquals(raw.localUri, raw.localPath)
        assertTrue(raw.localUri.startsWith("v1/persistent/order_images/orders/42/"))
        assertFalse(raw.localUri.startsWith("/"))

        val model = fixture.repository.getImagesByOrderId(OrderKey(42L)).single()
        val storedFile = requireNotNull(model.localPath).let(::File)
        assertTrue(storedFile.isFile)
        assertTrue(storedFile.canonicalPath.startsWith(pathsFactory.forScope(scope).persistentRoot.canonicalPath))
        assertEquals("image-a", storedFile.readText())

        fixture.repository.deleteImage(imageId)
        assertFalse(storedFile.exists())
        assertTrue(fixture.repository.getImagesByOrderId(OrderKey(42L)).isEmpty())
        fixture.registry.close()
    }

    @Test
    fun `same order and filename remain isolated across users`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = trackedScope(911, 912, 913)
        val scopeB = trackedScope(921, 922, 923)
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(11))
        fixture.registry.database(leaseA).orderDao().insertOrUpdate(OrderEntityDb(orderId = 77L))
        fixture.repository.addImage(
            OrderKey(77L),
            ImageType.AFTER_CARE,
            Uri.fromFile(source(fixture, leaseA, "same-a.jpg", "A")).toString(),
        )
        val fileA = File(requireNotNull(fixture.repository.getImagesByOrderId(OrderKey(77L)).single().localPath))

        val leaseB = fixture.registry.open(scopeB, SessionEpoch(12))
        fixture.registry.database(leaseB).orderDao().insertOrUpdate(OrderEntityDb(orderId = 77L))
        val idB = fixture.repository.addImage(
            OrderKey(77L),
            ImageType.AFTER_CARE,
            Uri.fromFile(source(fixture, leaseB, "same-b.jpg", "B")).toString(),
        ).id
        val fileB = File(requireNotNull(fixture.repository.getImagesByOrderId(OrderKey(77L)).single().localPath))
        assertNotEquals(fileA.canonicalPath, fileB.canonicalPath)
        assertEquals("B", fileB.readText())
        fixture.repository.deleteImage(idB)
        assertFalse(fileB.exists())

        fixture.registry.open(scopeA, SessionEpoch(13))
        val reopenedA = fixture.repository.getImagesByOrderId(OrderKey(77L)).single()
        assertEquals(fileA.canonicalPath, File(requireNotNull(reopenedA.localPath)).canonicalPath)
        assertEquals("A", fileA.readText())
        fixture.registry.close()
    }

    @Test
    fun `source captured by a revoked user cannot be imported into the next user`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = trackedScope(931, 932, 933)
        val scopeB = trackedScope(941, 942, 943)
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(21))
        val sourceA = source(fixture, leaseA, "late-a.jpg", "A")
        val leaseB = fixture.registry.open(scopeB, SessionEpoch(22))
        fixture.registry.database(leaseB).orderDao().insertOrUpdate(OrderEntityDb(orderId = 88L))

        val failure = runCatching {
            fixture.repository.addImage(
                OrderKey(88L),
                ImageType.BEFORE_CARE,
                Uri.fromFile(sourceA).toString(),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(fixture.repository.getImagesByOrderId(OrderKey(88L)).isEmpty())
        assertTrue(sourceA.exists())
        fixture.registry.close()
    }

    private fun fixture(applicationScope: kotlinx.coroutines.CoroutineScope): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val managedFiles = UserManagedFiles(context, pathsFactory, registry, Dispatchers.IO)
        return Fixture(
            registry = registry,
            repository = ImageRepository(UserDatabaseAccess(registry), managedFiles),
            managedFiles = managedFiles,
        )
    }

    private fun source(
        fixture: Fixture,
        lease: UserStorageLease,
        name: String,
        contents: String,
    ): File = fixture.managedFiles.sessionFile(lease, "photo_upload", name).apply {
            parentFile?.mkdirs()
            writeText(contents)
            sources += this
        }

    private fun trackedScope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val repository: ImageRepository,
        val managedFiles: UserManagedFiles,
    )
}
