package com.ytone.longcare.di

import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.system.ServicePhotoConfigProvider
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemConfigProviderModule {

    @Binds
    abstract fun bindFaceVerificationConfigProvider(
        impl: SystemConfigManager,
    ): FaceVerificationConfigProvider

    @Binds
    abstract fun bindServicePhotoConfigProvider(
        impl: SystemConfigManager,
    ): ServicePhotoConfigProvider

    @Binds
    @IntoSet
    abstract fun bindSystemConfigRuntimeCleanupHook(
        impl: SystemConfigManager,
    ): SessionRuntimeCleanupHook
}
