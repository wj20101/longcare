package com.ytone.longcare.di

import com.ytone.longcare.data.repository.SampleRepositoryImpl
import com.ytone.longcare.domain.impl.LoginRepositoryImpl
import com.ytone.longcare.domain.impl.ProfileRepositoryImpl
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.SampleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSampleRepository(impl: SampleRepositoryImpl): SampleRepository

    @Binds
    @Singleton
    abstract fun bindLoginRepository(impl: LoginRepositoryImpl): LoginRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}