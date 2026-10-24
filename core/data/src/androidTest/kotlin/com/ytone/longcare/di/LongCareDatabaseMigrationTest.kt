package com.ytone.longcare.di

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.MIGRATION_3_4
import com.ytone.longcare.data.database.entity.OrderEntityDb
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun oldVersionsAreCompletelyRebuiltAsCurrentVersion() {
        (1..2).forEach { version ->
            assertDestructiveRebuild(version, "longcare-destructive-v$version")
        }
    }

    @Test
    fun currentVersionDataSurvivesOrdinaryReopen() = runBlocking {
        val first = openDatabase(DATABASE_CURRENT)
        first.orderDao().insertOrUpdate(OrderEntityDb(orderId = 99L))
        first.close()

        val reopened = openDatabase(DATABASE_CURRENT)
        assertTrue(reopened.orderDao().exists(99L))
        reopened.close()
    }

    @Test
    fun version3MigrationPreservesProgressPhotosAndProjectSelectionWithoutInventingClockOrigin() = runBlocking {
        helper.createDatabase(DATABASE_V3, 3).apply {
            execSQL("INSERT INTO orders(order_id, plan_id, state) VALUES (42, 7, 1)")
            execSQL("INSERT INTO order_elder_info(order_id, elder_name) VALUES (42, 'Test elder')")
            execSQL("INSERT INTO order_local_states(order_id, local_status, local_start_timestamp, face_verification_completed, needs_sync) VALUES (42, 1, 1000, 1, 1)")
            execSQL("INSERT INTO order_projects(order_id, project_id, project_name, service_time, is_selected) VALUES (42, 9, 'Test project', 20, 1)")
            execSQL("INSERT INTO order_images(order_id, image_type, local_uri, upload_status) VALUES (42, 0, 'file:///pending.jpg', 0)")
            close()
        }

        // Validate the actual migration against Room's generated version 4 schema.
        helper.runMigrationsAndValidate(DATABASE_V3, 4, true, MIGRATION_3_4).close()
        val database = openDatabase(DATABASE_V3)
        try {
            assertEquals(7, requireNotNull(database.orderDao().getOrderById(42)).planId)
            val saved = requireNotNull(database.orderLocalStateDao().getByOrderId(42))
            assertEquals(1, saved.localStatus)
            assertEquals(1_000L, saved.localStartTimestamp)
            assertNull(saved.localStartElapsedRealtime)
            assertNull(saved.localStartBootCount)
            assertTrue(saved.faceVerificationCompleted)
            assertTrue(saved.needsSync)
            val image = database.orderImageDao().getImagesByOrderId(42).single()
            assertEquals("file:///pending.jpg", image.localUri)
            assertEquals(0, image.uploadStatus)
            assertEquals(listOf(9), database.orderProjectDao().getSelectedProjectIds(42))
            database.openHelper.readableDatabase.query("SELECT elder_name FROM order_elder_info WHERE order_id = 42").use {
                assertTrue(it.moveToFirst())
                assertEquals("Test elder", it.getString(0))
            }

            database.orderLocalStateDao().startService(42, 121_000L, 500L, 7)
            val recovered = requireNotNull(database.orderLocalStateDao().getByOrderId(42))
            assertEquals(1_000L, recovered.localStartTimestamp)
            assertNull(recovered.localStartElapsedRealtime)
            assertNull(recovered.localStartBootCount)
        } finally {
            database.close()
        }
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
        DatabaseModule.buildDatabase(context, name)

    private companion object {
        const val DATABASE_V3 = "longcare-destructive-v3"
        const val DATABASE_CURRENT = "longcare-current-version"
        val DATABASE_NAMES = (1..3).map { "longcare-destructive-v$it" } + DATABASE_CURRENT
    }
}
