package com.ytone.longcare.di

import com.ytone.longcare.common.network.FlavorInterceptorApplier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppFlavorInterceptorModule {
    @Binds
    @Singleton
    abstract fun bindFlavorInterceptorApplier(
        impl: AppFlavorInterceptorApplier,
    ): FlavorInterceptorApplier
}
