package com.ytone.longcare.features.location.di

import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @IntoSet
    fun provideLocationRuntimeCleanupHook(
        manager: LocationTrackingManager,
    ): SessionRuntimeCleanupHook = manager
}
