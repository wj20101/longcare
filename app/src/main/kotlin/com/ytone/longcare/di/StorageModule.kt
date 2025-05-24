package com.ytone.longcare.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ytone.longcare.domain.repository.DefaultUserSessionRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.Binds
import com.tencent.mmkv.MMKV
import com.ytone.longcare.data.storage.UserSpecificStorageManager
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModuleInternal {
    @Binds
    @Singleton
    abstract fun bindUserSessionRepository(impl: DefaultUserSessionRepository): UserSessionRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    // 这是你原有的 AppModule 中的 SharedPreferences provider
    // 如果它没有 Qualifier，你可以考虑添加一个，比如 @AppPrefs
    @Provides
    @Singleton
    // @AppPrefs // 可选，如果需要区分
    fun provideAppSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @DeviceIdStorage // 使用我们定义的 Qualifier
    fun provideDeviceIdSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        // 为设备 ID 使用一个专门的 SharedPreferences 文件
        return context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideMMKV(): MMKV {
        // MMKVUtils.init() should be called by MMKVInitializer at application startup
        return MMKV.defaultMMKV()
    }

    @Provides
    @Singleton
    fun provideUserSpecificStorageManager(
        userSessionRepository: UserSessionRepository
    ): UserSpecificStorageManager {
        return UserSpecificStorageManager(userSessionRepository)
    }
}