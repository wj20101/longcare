package com.ytone.longcare.di

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.data.database.LongCareDatabase
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongCareDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LongCareDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2PreservesSchemaAndAddsLocationMetadata() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            DatabaseModule.MIGRATION_1_2,
        )

        val columns = database.query("PRAGMA table_info(`order_locations`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns.containsAll(EXPECTED_LOCATION_COLUMNS))
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "longcare-migration-test"
        val EXPECTED_LOCATION_COLUMNS = setOf(
            "coord_type",
            "location_type",
            "trusted_level",
            "location_time",
        )
    }
}
