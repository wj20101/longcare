package com.ytone.longcare.features.home.di

import com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProvider
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeReportingModule {

    @Binds
    @Singleton
    abstract fun bindHomeLoginLogInfoProvider(
        impl: DefaultHomeLoginLogInfoProvider,
    ): HomeLoginLogInfoProvider
}
