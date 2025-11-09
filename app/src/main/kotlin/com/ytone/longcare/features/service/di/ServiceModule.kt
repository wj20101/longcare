package com.ytone.longcare.features.service.di

import android.content.Context
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 服务模块 - 提供依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun providePendingOrdersStorage(@ApplicationContext context: Context): PendingOrdersStorage {
        return PendingOrdersStorage(context, "pending_orders_storage")
    }
}