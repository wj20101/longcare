package com.ytone.longcare.features.identification.domain

import android.content.Context
import android.net.Uri
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.order.OrderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface UploadElderPhotoResult {
    data object Success : UploadElderPhotoResult

    data class Error(val message: String) : UploadElderPhotoResult
}

class UploadElderPhotoUseCase @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val cosRepository: CosRepository,
    private val orderRepository: OrderRepository,
) {
    suspend fun execute(photoUri: Uri, orderId: Long): UploadElderPhotoResult {
        val uploadParams = CosUtils.createUploadParams(
            context = applicationContext,
            fileUri = photoUri,
            folderType = CosConstants.DEFAULT_FOLDER_TYPE,
        )
        val uploadResult = cosRepository.uploadFile(uploadParams)

        if (!uploadResult.success || uploadResult.key == null) {
            return UploadElderPhotoResult.Error(uploadResult.errorMessage ?: "图片上传失败")
        }

        return when (val result = orderRepository.upUserStartImg(orderId, listOf(uploadResult.key))) {
            is ApiResult.Success -> UploadElderPhotoResult.Success
            is ApiResult.Failure -> UploadElderPhotoResult.Error(result.message)
            is ApiResult.Exception -> UploadElderPhotoResult.Error(
                result.exception.message ?: "网络错误，请检查网络连接",
            )
        }
    }
}
