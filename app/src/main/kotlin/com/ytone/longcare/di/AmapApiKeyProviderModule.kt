package com.ytone.longcare.di

import com.ytone.longcare.common.location.SystemConfigAmapApiKeyProvider
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AmapApiKeyProviderModule {
    @Binds
    @Singleton
    abstract fun bindAmapApiKeyProvider(
        impl: SystemConfigAmapApiKeyProvider
    ): AmapApiKeyProvider
}
