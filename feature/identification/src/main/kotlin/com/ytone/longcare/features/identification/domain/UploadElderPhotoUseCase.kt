package com.ytone.longcare.features.identification.domain

import android.net.Uri
import javax.inject.Inject

sealed interface UploadElderPhotoResult {
    data object Success : UploadElderPhotoResult

    data class Error(val message: String) : UploadElderPhotoResult
}

class UploadElderPhotoUseCase @Inject constructor(
    private val gateway: UploadElderPhotoGateway,
) {
    suspend fun execute(photoUri: Uri, orderId: Long): UploadElderPhotoResult {
        return when (val uploadResult = gateway.uploadPhoto(photoUri)) {
            is UploadElderPhotoSourceResult.Success -> {
                when (val orderResult = gateway.uploadOrderStartImage(orderId, uploadResult.uploadedKey)) {
                    UploadElderPhotoOrderResult.Success -> UploadElderPhotoResult.Success
                    is UploadElderPhotoOrderResult.Error -> UploadElderPhotoResult.Error(orderResult.message)
                }
            }

            is UploadElderPhotoSourceResult.Error -> UploadElderPhotoResult.Error(uploadResult.message)
        }
    }
}
