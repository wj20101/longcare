package com.ytone.longcare.di

import android.content.Context
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.data.database.entity.OrderLocalStateEntityDb
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
            assertTrue(requireNotNull(database.orderLocalStateDao().getByOrderId(42)).faceVerificationCompleted)
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
        setVersion(4)
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
            database.orderLocalStateDao().insertOrUpdate(OrderLocalStateEntityDb(orderId = 42, faceVerificationCompleted = true))
        } finally {
            database.close()
        }
    }

    private fun setVersion(version: Int) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = version }
    }
}
