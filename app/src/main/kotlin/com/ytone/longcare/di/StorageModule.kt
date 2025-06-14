package com.ytone.longcare.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ytone.longcare.data.storage.appDataStore
import com.ytone.longcare.domain.impl.DefaultUserSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.Binds
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

    @Provides
    @Singleton
    @AppPrefs
    fun provideAppSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @DeviceIdStorage
    fun provideDeviceIdSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @AppDataStore
    fun provideAppDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.appDataStore
    }
}