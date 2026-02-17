package com.ytone.longcare.features.servicecountdown.di

import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.service.ServiceCountdownSystemGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceCountdownGatewayModule {

    @Binds
    abstract fun bindServiceCountdownSystemGateway(
        impl: ServiceCountdownSystemGatewayImpl,
    ): ServiceCountdownSystemGateway
}
