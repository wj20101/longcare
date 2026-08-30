package com.ytone.longcare.di

import android.net.Uri
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.features.photoupload.upload.DefaultPhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Debug-only uploader that never resolves credentials or initializes a vendor SDK. */
@Singleton
internal class DebugPhotoCloudUploader @Inject constructor() : PhotoCloudUploader {
    override suspend fun upload(uri: Uri, folderType: Int): UploadedPhoto {
        val uriDigest =
            MessageDigest.getInstance("SHA-256")
                .digest(uri.toString().toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
                .take(16)
        return UploadedPhoto(
            key = "mock-only/not-for-production/$folderType/$uriDigest.jpg",
        )
    }
}

internal fun selectDebugPhotoCloudUploader(
    useMockData: Boolean,
    realUploader: PhotoCloudUploader,
    fakeUploader: PhotoCloudUploader,
): PhotoCloudUploader = if (useMockData) fakeUploader else realUploader

@Module
@InstallIn(SingletonComponent::class)
internal object PhotoCloudUploadModule {
    @Provides
    @Singleton
    fun providePhotoCloudUploader(
        realUploader: DefaultPhotoCloudUploader,
        fakeUploader: DebugPhotoCloudUploader,
    ): PhotoCloudUploader =
        selectDebugPhotoCloudUploader(
            useMockData = BuildConfig.USE_MOCK_DATA,
            realUploader = realUploader,
            fakeUploader = fakeUploader,
        )
}
