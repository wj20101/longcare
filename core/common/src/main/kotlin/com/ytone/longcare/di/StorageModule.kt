package com.ytone.longcare.di

import android.content.Context
import android.content.SharedPreferences
import com.ytone.longcare.common.utils.DeviceRuntimeState
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
        return context.getSharedPreferences(
            DeviceRuntimeState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    }
}
