package com.ytone.longcare.features.photoupload.upload

import android.content.Context
import android.net.Uri
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

data class UploadedPhoto(
    val key: String,
)

class PhotoCloudUploadException(message: String) : Exception(message)

interface PhotoCloudUploader {
    suspend fun upload(
        uri: Uri,
        folderType: Int = CosConstants.DEFAULT_FOLDER_TYPE,
    ): UploadedPhoto
}

@Singleton
class DefaultPhotoCloudUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cosRepository: CosRepository,
) : PhotoCloudUploader {
    override suspend fun upload(
        uri: Uri,
        folderType: Int,
    ): UploadedPhoto {
        val result =
            cosRepository.uploadFile(
                CosUtils.createUploadParams(
                    context = context,
                    fileUri = uri,
                    folderType = folderType,
                )
            )
        val key = result.key
        if (!result.success || key.isNullOrBlank()) {
            throw PhotoCloudUploadException(
                result.errorMessage?.takeIf(String::isNotBlank)
                    ?: "图片上传未返回有效文件信息"
            )
        }
        return UploadedPhoto(key = key)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotoCloudUploadModule {
    @Binds
    abstract fun bindPhotoCloudUploader(
        implementation: DefaultPhotoCloudUploader,
    ): PhotoCloudUploader
}
