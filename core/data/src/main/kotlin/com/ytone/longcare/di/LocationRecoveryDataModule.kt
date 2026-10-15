package com.ytone.longcare.di

import com.ytone.longcare.data.repository.DataStoreLocationRecoveryStore
import com.ytone.longcare.domain.location.LocationRecoveryStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationRecoveryDataModule {
    @Binds @Singleton
    abstract fun bindLocationRecoveryStore(impl: DataStoreLocationRecoveryStore): LocationRecoveryStore
}
