package com.ytone.longcare.di

import android.content.Context
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.data.database.entity.OrderLocalStateEntityDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseRetentionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val name = "order-retention-test"

    @After
    fun cleanup() { context.deleteDatabase(name) }

    @Test
    fun `ordinary reopen retains pending photos and local progress`() = runTest {
        seedPendingPhoto()
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            assertEquals("file:///pending.jpg", database.orderImageDao().getImagesByOrderId(42).single().localUri)
            val saved = requireNotNull(database.orderLocalStateDao().getByOrderId(42))
            assertTrue(saved.faceVerificationCompleted)
            assertEquals(1_000L, saved.localStartTimestamp)
            assertEquals(500L, saved.localStartElapsedRealtime)
            assertEquals(7, saved.localStartBootCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun `concurrent confirmations preserve one complete first origin`() = runTest {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            val local = database.orderLocalStateDao()
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42))
            val ready = CompletableDeferred<Unit>()
            val origins = listOf(Triple(1_000L, 500L, 7), Triple(2_000L, 100L, 8))
            val confirmations = origins.map { (wall, elapsed, boot) ->
                async(Dispatchers.Default) {
                    ready.await()
                    local.startService(42, wall, elapsed, boot)
                }
            }
            ready.complete(Unit)
            confirmations.awaitAll()

            val saved = requireNotNull(local.getByOrderId(42))
            assertTrue(Triple(saved.localStartTimestamp, saved.localStartElapsedRealtime, saved.localStartBootCount) in origins)
            assertEquals(1, saved.localStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun `late local state initialization preserves a confirmed service start`() = runTest {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            val local = database.orderLocalStateDao()
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42))
            local.startService(42, 1_000L, 500L, 7)
            local.updateFaceVerification(42, true)
            // A detail refresh that observed a missing row before the start must not replace it.
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42))

            val saved = requireNotNull(local.getByOrderId(42))
            assertEquals(1_000L, saved.localStartTimestamp)
            assertEquals(500L, saved.localStartElapsedRealtime)
            assertEquals(7, saved.localStartBootCount)
            assertEquals(1, saved.localStatus)
            assertTrue(saved.faceVerificationCompleted)
        } finally {
            database.close()
        }
    }

    @Test
    fun `repeated countdown preparation neither moves the start nor reopens a completed service`() = runTest {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            val local = database.orderLocalStateDao()
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42))
            local.startService(42, 1_000L, 500L, 7)
            local.startService(42, 121_000L, 20L, 8)
            val repeated = requireNotNull(local.getByOrderId(42))
            assertEquals(1_000L, repeated.localStartTimestamp)
            assertEquals(500L, repeated.localStartElapsedRealtime)
            assertEquals(7, repeated.localStartBootCount)

            local.endService(42, 181_000L)
            local.startService(42, 241_000L, 120_020L, 8)
            val ended = requireNotNull(local.getByOrderId(42))
            assertEquals(1_000L, ended.localStartTimestamp)
            assertEquals(500L, ended.localStartElapsedRealtime)
            assertEquals(7, ended.localStartBootCount)
            assertEquals(181_000L, ended.localEndTimestamp)
            assertEquals(2, ended.localStatus)
        } finally {
            database.close()
        }
    }

    @Test
    fun `legacy start without monotonic origin cannot be rebased by countdown recovery`() = runTest {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            val local = database.orderLocalStateDao()
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42, localStatus = 1, localStartTimestamp = 1_000L))
            local.startService(42, 121_000L, 500L, 7)

            val saved = requireNotNull(local.getByOrderId(42))
            assertEquals(1_000L, saved.localStartTimestamp)
            assertNull(saved.localStartElapsedRealtime)
            assertNull(saved.localStartBootCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun `completed row without a start cannot be reopened by late confirmation`() = runTest {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            val local = database.orderLocalStateDao()
            local.insertIfAbsent(OrderLocalStateEntityDb(orderId = 42))
            local.endService(42, 1_000L)
            local.startService(42, 2_000L, 500L, 7)

            val saved = requireNotNull(local.getByOrderId(42))
            assertEquals(2, saved.localStatus)
            assertEquals(1_000L, saved.localEndTimestamp)
            assertNull(saved.localStartTimestamp)
            assertNull(saved.localStartElapsedRealtime)
            assertNull(saved.localStartBootCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun `only approved historical versions may be rebuilt`() = runTest {
        for (version in listOf(1, 2)) {
            seedPendingPhoto()
            setVersion(version)
            val database = DatabaseModule.buildDatabase(context, name)
            try {
                assertTrue(database.orderImageDao().getImagesByOrderId(42).isEmpty())
                assertFalse(database.orderDao().exists(42))
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
    }

    @Test
    fun `unexpected version fails without destroying pending photos`() = runTest {
        seedPendingPhoto()
        setVersion(5)
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            assertThrows(IllegalStateException::class.java) { database.openHelper.writableDatabase }
        } finally {
            database.close()
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.rawQuery("SELECT local_uri FROM order_images WHERE order_id = 42", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("file:///pending.jpg", cursor.getString(0))
            }
        }
    }

    private suspend fun seedPendingPhoto() {
        val database = DatabaseModule.buildDatabase(context, name)
        try {
            database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 42))
            database.orderImageDao().insert(OrderImageEntityDb(orderId = 42, imageType = 0, localUri = "file:///pending.jpg"))
            database.orderLocalStateDao().insertIfAbsent(OrderLocalStateEntityDb(orderId = 42, faceVerificationCompleted = true))
            database.orderLocalStateDao().startService(42, 1_000L, 500L, 7)
        } finally {
            database.close()
        }
    }

    private fun setVersion(version: Int) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = version }
    }
}
