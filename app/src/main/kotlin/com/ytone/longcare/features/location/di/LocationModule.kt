package com.ytone.longcare.features.location.di

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.ytone.longcare.features.location.provider.SystemLocationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    
    @Provides
    @Singleton
    fun provideMainThreadExecutor(@ApplicationContext context: Context): Executor {
        return ContextCompat.getMainExecutor(context)
    }
    
    @Provides
    @Singleton
    fun provideSystemLocationProvider(
        locationManager: LocationManager,
        mainThreadExecutor: Executor
    ): SystemLocationProvider {
        return SystemLocationProvider(locationManager, mainThreadExecutor)
    }
}