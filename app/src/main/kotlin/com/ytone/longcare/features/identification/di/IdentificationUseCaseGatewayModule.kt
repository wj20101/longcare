package com.ytone.longcare.features.identification.di

import com.ytone.longcare.features.identification.data.VerifyServicePersonDataGatewayImpl
import com.ytone.longcare.features.identification.data.UploadElderPhotoGatewayImpl
import com.ytone.longcare.features.identification.domain.UploadElderPhotoGateway
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDataGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IdentificationUseCaseGatewayModule {

    @Binds
    abstract fun bindVerifyServicePersonDataGateway(
        impl: VerifyServicePersonDataGatewayImpl,
    ): VerifyServicePersonDataGateway

    @Binds
    abstract fun bindUploadElderPhotoGateway(
        impl: UploadElderPhotoGatewayImpl,
    ): UploadElderPhotoGateway
}
