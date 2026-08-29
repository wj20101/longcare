package com.ytone.longcare.features.service.di

import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    @IntoSet
    abstract fun bindServiceNotificationRuntimeCleanupHook(
        manager: ServiceTimeNotificationManager,
    ): SessionRuntimeCleanupHook
}
