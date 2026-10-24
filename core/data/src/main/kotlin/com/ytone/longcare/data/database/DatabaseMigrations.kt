package com.ytone.longcare.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve active orders and pending photos; old starts have no trustworthy monotonic origin. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE order_local_states ADD COLUMN local_start_elapsed_realtime INTEGER")
        db.execSQL("ALTER TABLE order_local_states ADD COLUMN local_start_boot_count INTEGER")
    }
}
