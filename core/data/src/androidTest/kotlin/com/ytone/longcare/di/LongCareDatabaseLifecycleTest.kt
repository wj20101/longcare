package com.ytone.longcare.di

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.OrderEntityDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongCareDatabaseLifecycleTest {
    private lateinit var database: LongCareDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LongCareDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun orderFlowEmitsInsertUpdateAndDelete() = runBlocking {
        val dao = database.orderDao()
        val changes = Channel<OrderEntityDb?>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.Default) {
            dao.observeOrderById(42L).collect { changes.send(it) }
        }
        try {
            assertNull(withTimeout(10_000) { changes.receive() })
            dao.insertOrUpdate(OrderEntityDb(orderId = 42L, state = 0))
            assertEquals(0, withTimeout(10_000) { changes.receive() }?.state)
            dao.updateState(42L, 1)
            assertEquals(1, withTimeout(10_000) { changes.receive() }?.state)
            dao.deleteById(42L)
            assertNull(withTimeout(10_000) { changes.receive() })
        } finally {
            observer.cancelAndJoin()
            changes.close()
        }
    }

    @Test
    fun cancellingFlowLeavesDatabaseUsable() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val observer = launch(Dispatchers.Default) {
            database.orderDao().observeAllOrders().collect { started.complete(Unit) }
        }
        try {
            withTimeout(10_000) { started.await() }
        } finally {
            observer.cancelAndJoin()
        }
        database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 43L))
        assertTrue(database.orderDao().exists(43L))
    }

    @Test
    fun cancellingTransactionRollsBackWriteAndReleasesConnection() = runBlocking {
        val inserted = CompletableDeferred<Unit>()
        val transaction = launch(Dispatchers.Default) {
            database.withTransaction {
                database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 44L))
                inserted.complete(Unit)
                awaitCancellation()
            }
        }
        try {
            withTimeout(10_000) { inserted.await() }
        } finally {
            transaction.cancelAndJoin()
        }
        assertNull(database.orderDao().getOrderById(44L))
        database.orderDao().insertOrUpdate(OrderEntityDb(orderId = 45L))
        assertTrue(database.orderDao().exists(45L))
    }

    @Test
    fun suspendingQueryAfterCloseFailsExplicitly() {
        runBlocking { database.orderDao().exists(1L) }
        database.close()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.orderDao().getOrderById(1L) }
        }
    }

    @Test
    fun invalidationRefreshAfterCloseFailsExplicitly() {
        runBlocking { database.orderDao().exists(1L) }
        database.close()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.invalidationTracker.refresh("orders") }
        }
    }
}
