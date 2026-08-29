package com.ytone.longcare.data.userstorage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.data.database.entity.OrderLocalStateEntityDb
import com.ytone.longcare.data.database.entity.OrderProjectEntityDb
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
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
class UserDatabaseFactoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val factory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory)
    private val scopeA = UserScopeKey(8101, 8201, 8301)
    private val scopeB = UserScopeKey(8102, 8202, 8302)

    @Before
    @After
    fun cleanUp() {
        listOf(scopeA, scopeB).forEach { scope ->
            val paths = pathsFactory.forScope(scope)
            context.deleteDatabase(paths.databaseFile.name)
            paths.dataStoreFile.delete()
            paths.namespaceRoot.deleteRecursively()
            paths.sessionRoot.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun metadataIsInitializedVerifiedAndDatabaseCanBeClosed() = runBlocking {
        val database = factory.open(scopeA)
        val metadata = database.userNamespaceMetadataDao().get()

        assertEquals(scopeA.namespaceId().value, metadata?.namespaceId)
        assertEquals(scopeA.companyId, metadata?.companyId)
        assertEquals(scopeA.accountId, metadata?.accountId)
        assertEquals(scopeA.userId, metadata?.userId)
        database.close()
        assertFalse(database.isOpen)

        val reopened = factory.open(scopeA)
        assertTrue(reopened.isOpen)
        reopened.close()
    }

    @Test
    fun mismatchedMetadataFailsClosed() = runBlocking {
        withDatabase(scopeA) { database ->
            database.openHelper.writableDatabase.execSQL(
                "UPDATE user_namespace_metadata SET user_id = ? WHERE id = 1",
                arrayOf(scopeB.userId),
            )
        }

        val error = runCatching { factory.open(scopeA) }.exceptionOrNull()

        assertTrue(error is NamespaceOwnershipException)
    }

    @Test
    fun destructiveRebuildOnlyTouchesRequestedUserDatabase() = runBlocking {
        val pathsA = pathsFactory.forScope(scopeA)
        val pathsB = pathsFactory.forScope(scopeB)
        assertNotEquals(pathsA.databaseFile, pathsB.databaseFile)

        withDatabase(scopeA) { it.orderDao().insertOrUpdate(OrderEntityDb(orderId = 77L)) }
        withDatabase(scopeB) { it.orderDao().insertOrUpdate(OrderEntityDb(orderId = 77L)) }

        SQLiteDatabase.openDatabase(
            pathsA.databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database -> database.version = 3 }

        withDatabase(scopeA) { rebuiltA ->
            assertFalse(rebuiltA.orderDao().exists(77L))
            assertEquals(scopeA.namespaceId().value, rebuiltA.userNamespaceMetadataDao().get()?.namespaceId)
        }
        withDatabase(scopeB) { untouchedB ->
            assertTrue(untouchedB.orderDao().exists(77L))
            assertEquals(scopeB.namespaceId().value, untouchedB.userNamespaceMetadataDao().get()?.namespaceId)
        }
    }

    @Test
    fun sameBusinessKeysAreFullyIsolatedAcrossPhysicalUserDatabases() = runBlocking {
        val orderId = 909L
        seedUser(scopeA, orderId, state = 1, projectId = 101, localStatus = 11, marker = "A")
        seedUser(scopeB, orderId, state = 2, projectId = 202, localStatus = 22, marker = "B")

        withDatabase(scopeA) { databaseA ->
            assertEquals(1, databaseA.orderDao().getOrderById(orderId)?.state)
            assertEquals(listOf(101), databaseA.orderProjectDao().getProjectsByOrderId(orderId).map { it.projectId })
            assertEquals(11, databaseA.orderLocalStateDao().getByOrderId(orderId)?.localStatus)
            assertEquals("v1/persistent/order_images/A.jpg", databaseA.orderImageDao().getImagesByOrderId(orderId).single().localPath)
            assertEquals(scopeA.namespaceId().value, databaseA.userNamespaceMetadataDao().get()?.namespaceId)
            databaseA.orderDao().deleteById(orderId)
            assertTrue(databaseA.orderImageDao().getImagesByOrderId(orderId).isEmpty())
        }

        withDatabase(scopeB) { databaseB ->
            assertEquals(2, databaseB.orderDao().getOrderById(orderId)?.state)
            assertEquals(listOf(202), databaseB.orderProjectDao().getProjectsByOrderId(orderId).map { it.projectId })
            assertEquals(22, databaseB.orderLocalStateDao().getByOrderId(orderId)?.localStatus)
            assertEquals("v1/persistent/order_images/B.jpg", databaseB.orderImageDao().getImagesByOrderId(orderId).single().localPath)
            assertEquals(scopeB.namespaceId().value, databaseB.userNamespaceMetadataDao().get()?.namespaceId)
        }
    }

    private suspend fun seedUser(
        scopeKey: UserScopeKey,
        orderId: Long,
        state: Int,
        projectId: Int,
        localStatus: Int,
        marker: String,
    ) {
        withDatabase(scopeKey) { database ->
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = orderId, state = state))
            database.orderProjectDao().insertOrUpdate(
                OrderProjectEntityDb(
                    orderId = orderId,
                    projectId = projectId,
                    projectName = "project-$marker",
                    isSelected = true,
                )
            )
            database.orderLocalStateDao().insertOrUpdate(
                OrderLocalStateEntityDb(orderId = orderId, localStatus = localStatus)
            )
            database.orderImageDao().insert(
                OrderImageEntityDb(
                    orderId = orderId,
                    imageType = 1,
                    localUri = "v1/persistent/order_images/$marker.jpg",
                    localPath = "v1/persistent/order_images/$marker.jpg",
                )
            )
        }
    }

    private suspend fun <T> withDatabase(
        scopeKey: UserScopeKey,
        block: suspend (LongCareDatabase) -> T,
    ): T {
        val database = factory.open(scopeKey)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }
}
