package com.ytone.longcare.di

import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FaceVerificationConfigModule {

    @Binds
    abstract fun bindFaceVerificationConfigProvider(
        impl: SystemConfigManager,
    ): FaceVerificationConfigProvider
}

