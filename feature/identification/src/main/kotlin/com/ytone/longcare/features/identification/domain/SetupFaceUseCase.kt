package com.ytone.longcare.features.identification.domain

import java.io.File
import javax.inject.Inject

sealed interface SetupFaceResult {
    data object Success : SetupFaceResult

    data class Error(val failure: SetupFaceFailure) : SetupFaceResult
}

sealed interface SetupFaceFailure {
    data object CurrentUserUnavailable : SetupFaceFailure

    data class ImageUpload(val detail: String?) : SetupFaceFailure

    data class ServerRejected(val message: String?) : SetupFaceFailure

    data object NetworkError : SetupFaceFailure
}

class SetupFaceUseCase @Inject constructor(
    private val gateway: SetupFaceGateway,
) {
    suspend fun execute(
        imageFile: File,
        base64Image: String,
        currentUserId: Int?,
    ): SetupFaceResult {
        if (currentUserId == null) {
            return SetupFaceResult.Error(SetupFaceFailure.CurrentUserUnavailable)
        }

        val uploadResult = gateway.uploadFaceImage(imageFile)
        if (uploadResult is SetupFaceUploadResult.Error) {
            return SetupFaceResult.Error(SetupFaceFailure.ImageUpload(uploadResult.detail))
        }
        val uploadedKey = (uploadResult as SetupFaceUploadResult.Success).uploadedKey

        return when (val setFaceResult = gateway.setFaceOnServer(base64Image, uploadedKey)) {
            SetupFaceServerResult.Success -> {
                SetupFaceResult.Success
            }

            is SetupFaceServerResult.Rejected -> SetupFaceResult.Error(
                SetupFaceFailure.ServerRejected(setFaceResult.message),
            )
            SetupFaceServerResult.NetworkError -> SetupFaceResult.Error(
                SetupFaceFailure.NetworkError,
            )
        }
    }
}
