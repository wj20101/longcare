package com.ytone.longcare.di

import com.ytone.longcare.features.photoupload.upload.DefaultPhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotoCloudUploadModule {
    @Binds
    @Singleton
    abstract fun bindPhotoCloudUploader(
        implementation: DefaultPhotoCloudUploader,
    ): PhotoCloudUploader
}
