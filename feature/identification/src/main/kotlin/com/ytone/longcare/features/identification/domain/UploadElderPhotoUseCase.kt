package com.ytone.longcare.features.identification.domain

import android.net.Uri
import javax.inject.Inject

sealed interface UploadElderPhotoResult {
    data object Success : UploadElderPhotoResult

    data class Error(val failure: UploadElderPhotoFailure) : UploadElderPhotoResult
}

sealed interface UploadElderPhotoFailure {
    data class ImageUpload(val detail: String?) : UploadElderPhotoFailure

    data class ServerRejected(val message: String?) : UploadElderPhotoFailure

    data object NetworkError : UploadElderPhotoFailure
}

class UploadElderPhotoUseCase @Inject constructor(
    private val gateway: UploadElderPhotoGateway,
) {
    suspend fun execute(photoUri: Uri, orderId: Long): UploadElderPhotoResult {
        return when (val uploadResult = gateway.uploadPhoto(photoUri)) {
            is UploadElderPhotoSourceResult.Success -> {
                when (val orderResult = gateway.uploadOrderStartImage(orderId, uploadResult.uploadedKey)) {
                    UploadElderPhotoOrderResult.Success -> UploadElderPhotoResult.Success
                    is UploadElderPhotoOrderResult.Rejected -> UploadElderPhotoResult.Error(
                        UploadElderPhotoFailure.ServerRejected(orderResult.message),
                    )
                    UploadElderPhotoOrderResult.NetworkError -> UploadElderPhotoResult.Error(
                        UploadElderPhotoFailure.NetworkError,
                    )
                }
            }

            is UploadElderPhotoSourceResult.Error -> UploadElderPhotoResult.Error(
                UploadElderPhotoFailure.ImageUpload(uploadResult.detail),
            )
        }
    }
}
