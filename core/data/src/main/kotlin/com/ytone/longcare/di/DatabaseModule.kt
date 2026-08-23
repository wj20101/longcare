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
 * 本地订单数据是服务端缓存。版本缺少迁移路径时按已确认策略完整重建。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
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
            .fallbackToDestructiveMigration(dropAllTables = true)
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
