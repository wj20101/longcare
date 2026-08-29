package com.ytone.longcare.features.servicecountdown.di

import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownAppLauncher
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.shell.servicecountdown.ServiceCountdownAppLauncherImpl
import com.ytone.longcare.features.servicecountdown.service.ServiceCountdownSystemGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceCountdownGatewayModule {

    @Binds
    abstract fun bindServiceCountdownAppLauncher(
        impl: ServiceCountdownAppLauncherImpl,
    ): ServiceCountdownAppLauncher

    @Binds
    abstract fun bindServiceCountdownSystemGateway(
        impl: ServiceCountdownSystemGatewayImpl,
    ): ServiceCountdownSystemGateway

    @Binds
    @IntoSet
    abstract fun bindServiceCountdownRuntimeCleanupHook(
        impl: ServiceCountdownSystemGatewayImpl,
    ): SessionRuntimeCleanupHook
}
