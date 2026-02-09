package com.ytone.longcare.features.location.di

import com.ytone.longcare.features.location.core.DefaultLocationFacade
import com.ytone.longcare.features.location.core.LocationFacade
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationCoreBindModule {

    @Binds
    @Singleton
    abstract fun bindLocationFacade(impl: DefaultLocationFacade): LocationFacade
}
