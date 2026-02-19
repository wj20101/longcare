package com.ytone.longcare.di

import com.ytone.longcare.network.interceptor.RequestCryptoProvider
import com.ytone.longcare.network.interceptor.RequestCryptoProviderImpl
import com.ytone.longcare.network.interceptor.RequestDeviceInfoProvider
import com.ytone.longcare.network.interceptor.RequestDeviceInfoProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBridgeModule {

    @Binds
    @Singleton
    abstract fun bindRequestCryptoProvider(
        impl: RequestCryptoProviderImpl,
    ): RequestCryptoProvider

    @Binds
    @Singleton
    abstract fun bindRequestDeviceInfoProvider(
        impl: RequestDeviceInfoProviderImpl,
    ): RequestDeviceInfoProvider
}

