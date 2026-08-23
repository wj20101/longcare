package com.ytone.longcare.di

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.OrderEntityDb
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongCareDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LongCareDatabase::class.java,
    )

    @After
    fun cleanup() {
        DATABASE_NAMES.forEach(context::deleteDatabase)
    }

    @Test
    @Throws(IOException::class)
    fun oldVersionsAreCompletelyRebuiltAsVersion3() {
        (1..2).forEach { version ->
            assertDestructiveRebuild(version, "longcare-destructive-v$version")
        }
    }

    @Test
    fun version3DataSurvivesOrdinaryReopen() = runBlocking {
        val first = openDatabase(DATABASE_V3)
        first.orderDao().insertOrUpdate(OrderEntityDb(orderId = 99L))
        first.close()

        val reopened = openDatabase(DATABASE_V3)
        assertTrue(reopened.orderDao().exists(99L))
        reopened.close()
    }

    private fun assertDestructiveRebuild(fromVersion: Int, name: String) {
        helper.createDatabase(name, fromVersion).apply {
            execSQL("CREATE TABLE IF NOT EXISTS legacy_sentinel(value TEXT NOT NULL)")
            execSQL("INSERT INTO legacy_sentinel(value) VALUES ('must be deleted')")
            execSQL("INSERT INTO orders(order_id) VALUES (123)")
            close()
        }

        val database = openDatabase(name)
        val sqlite = database.openHelper.writableDatabase
        assertEquals(LongCareDatabase.DATABASE_VERSION, sqlite.version)
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertFalse("location outbox must not exist", "location_upload_outbox" in tables)
        assertFalse("legacy location table must not exist", "order_locations" in tables)
        assertFalse("dropAllTables must remove non-Room tables", "legacy_sentinel" in tables)
        val orderCount = sqlite.query("SELECT COUNT(*) FROM orders").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(0, orderCount)
        database.close()
    }

    private fun openDatabase(name: String): LongCareDatabase =
        Room.databaseBuilder(context, LongCareDatabase::class.java, name)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val DATABASE_V3 = "longcare-destructive-v3"
        val DATABASE_NAMES = (1..3).map { "longcare-destructive-v$it" }
    }
}
