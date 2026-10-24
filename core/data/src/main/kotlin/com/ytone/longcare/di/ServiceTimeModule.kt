package com.ytone.longcare.di

import com.ytone.longcare.data.time.AndroidServiceTimeProvider
import com.ytone.longcare.domain.time.ServiceTimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceTimeModule {
    @Binds
    @Singleton
    abstract fun bindServiceTimeProvider(impl: AndroidServiceTimeProvider): ServiceTimeProvider
}
