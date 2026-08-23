package com.ytone.longcare.features.identification.data

import android.content.Context
import android.net.Uri
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.identification.domain.UploadElderPhotoGateway
import com.ytone.longcare.features.identification.domain.UploadElderPhotoOrderResult
import com.ytone.longcare.features.identification.domain.UploadElderPhotoSourceResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UploadElderPhotoGatewayImpl @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val cosRepository: CosRepository,
    private val orderRepository: OrderRepository,
) : UploadElderPhotoGateway {

    override suspend fun uploadPhoto(photoUri: Uri): UploadElderPhotoSourceResult {
        val uploadParams = CosUtils.createUploadParams(
            context = applicationContext,
            fileUri = photoUri,
            folderType = CosConstants.DEFAULT_FOLDER_TYPE,
        )
        val uploadResult = cosRepository.uploadFile(uploadParams)
        val uploadedKey = uploadResult.key

        return if (!uploadResult.success || uploadedKey == null) {
            UploadElderPhotoSourceResult.Error(uploadResult.errorMessage)
        } else {
            UploadElderPhotoSourceResult.Success(uploadedKey = uploadedKey)
        }
    }

    override suspend fun uploadOrderStartImage(
        orderId: Long,
        uploadedKey: String,
    ): UploadElderPhotoOrderResult {
        return when (val result = orderRepository.upUserStartImg(orderId, listOf(uploadedKey))) {
            is ApiResult.Success -> UploadElderPhotoOrderResult.Success
            is ApiResult.Failure -> UploadElderPhotoOrderResult.Rejected(
                result.message.takeIf(String::isNotBlank),
            )
            is ApiResult.Exception -> UploadElderPhotoOrderResult.NetworkError
        }
    }
}
