package com.ytone.longcare.di

import com.ytone.longcare.common.utils.FaceVerifier
import com.ytone.longcare.common.utils.FaceVerificationManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FaceVerificationModule {

    @Binds
    @Singleton
    abstract fun bindFaceVerifier(impl: FaceVerificationManager): FaceVerifier
}
