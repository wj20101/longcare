package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.FaceSetupRequestRepository
import com.ytone.longcare.domain.faceauth.FaceSetupRequestResult
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import java.io.File

internal sealed interface FaceSetupPreparation {
    data class Ready(
        val imageFile: File,
        val base64Image: String,
        val request: FaceVerificationRequest,
    ) : FaceSetupPreparation

    data class Error(val failure: FaceSetupPreparationFailure) : FaceSetupPreparation
}

internal enum class FaceSetupPreparationFailure {
    IMAGE_FILE_MISSING,
    CURRENT_USER_UNAVAILABLE,
    CURRENT_USER_INCOMPLETE,
}

internal suspend fun prepareFaceSetupVerificationInput(
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    faceSetupRequestRepository: FaceSetupRequestRepository,
): FaceSetupPreparation {
    val imageFile = File(imagePath)
    if (!imageFile.exists()) {
        return FaceSetupPreparation.Error(FaceSetupPreparationFailure.IMAGE_FILE_MISSING)
    }

    val base64Image = faceDataSource.imageFileToBase64(imageFile)
    val request = when (val result = faceSetupRequestRepository.createFaceSetupRequest(
        orderNo = createFaceSetupOrderNo(),
        sourcePhotoBase64 = base64Image,
    )) {
        is FaceSetupRequestResult.Ready -> result.request
        FaceSetupRequestResult.SessionUnavailable -> return FaceSetupPreparation.Error(
            FaceSetupPreparationFailure.CURRENT_USER_UNAVAILABLE
        )
        FaceSetupRequestResult.IdentityIncomplete -> return FaceSetupPreparation.Error(
            FaceSetupPreparationFailure.CURRENT_USER_INCOMPLETE
        )
    }
    return FaceSetupPreparation.Ready(
        imageFile = imageFile,
        base64Image = base64Image,
        request = request
    )
}
