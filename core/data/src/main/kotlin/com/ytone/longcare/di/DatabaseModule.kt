package com.ytone.longcare.di

import android.content.Context
import androidx.room.Room
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
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
 * 仅重建已确认可丢弃的 v1/v2 数据；后续升级必须迁移本地状态与未上传照片记录。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideLongCareDatabase(
        @ApplicationContext context: Context
    ): LongCareDatabase = buildDatabase(context)

    internal fun buildDatabase(
        context: Context,
        name: String = LongCareDatabase.DATABASE_NAME,
    ): LongCareDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = LongCareDatabase::class.java,
            name = name
        )
            .fallbackToDestructiveMigrationFrom(true, 1, 2)
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

}
