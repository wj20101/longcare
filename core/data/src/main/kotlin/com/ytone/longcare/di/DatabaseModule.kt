package com.ytone.longcare.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderLocationDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库DI Module
 *
 * 提供Room数据库及其DAO的依赖注入。
 * 数据库升级必须提供显式迁移，禁止因初始化或迁移异常静默清空业务数据。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE order_locations ADD COLUMN coord_type TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE order_locations ADD COLUMN location_type INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE order_locations ADD COLUMN trusted_level INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE order_locations ADD COLUMN location_time INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideLongCareDatabase(
        @ApplicationContext context: Context
    ): LongCareDatabase = buildDatabase(context)

    private fun buildDatabase(context: Context): LongCareDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = LongCareDatabase::class.java,
            name = LongCareDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideOrderDao(database: LongCareDatabase): OrderDao {
        return database.orderDao()
    }

    @Provides
    fun provideOrderElderInfoDao(database: LongCareDatabase): OrderElderInfoDao {
        return database.orderElderInfoDao()
    }

    @Provides
    fun provideOrderLocalStateDao(database: LongCareDatabase): OrderLocalStateDao {
        return database.orderLocalStateDao()
    }

    @Provides
    fun provideOrderProjectDao(database: LongCareDatabase): OrderProjectDao {
        return database.orderProjectDao()
    }

    @Provides
    fun provideOrderImageDao(database: LongCareDatabase): OrderImageDao {
        return database.orderImageDao()
    }

    @Provides
    fun provideOrderLocationDao(database: LongCareDatabase): OrderLocationDao {
        return database.orderLocationDao()
    }
}
