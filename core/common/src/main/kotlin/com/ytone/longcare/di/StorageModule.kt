package com.ytone.longcare.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    @DeviceIdStorage
    fun provideDeviceIdSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
    }
}
