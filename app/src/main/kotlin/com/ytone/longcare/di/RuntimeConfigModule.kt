package com.ytone.longcare.di

import com.ytone.longcare.common.config.RuntimeConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeConfigModule {
    @Binds
    abstract fun bindRuntimeConfigProvider(
        impl: AppRuntimeConfigProvider,
    ): RuntimeConfigProvider
}
