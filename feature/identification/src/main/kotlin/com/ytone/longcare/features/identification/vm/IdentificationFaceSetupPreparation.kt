package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.model.User
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
    currentUser: User?,
): FaceSetupPreparation {
    val imageFile = File(imagePath)
    if (!imageFile.exists()) {
        return FaceSetupPreparation.Error(FaceSetupPreparationFailure.IMAGE_FILE_MISSING)
    }

    val base64Image = faceDataSource.imageFileToBase64(imageFile)
    if (currentUser == null) {
        return FaceSetupPreparation.Error(FaceSetupPreparationFailure.CURRENT_USER_UNAVAILABLE)
    }

    if (currentUser.userName.isBlank() || currentUser.identityCardNumber.isBlank()) {
        return FaceSetupPreparation.Error(FaceSetupPreparationFailure.CURRENT_USER_INCOMPLETE)
    }

    val request = createFaceVerificationRequest(
        name = currentUser.userName,
        idNo = currentUser.identityCardNumber,
        orderNo = createFaceSetupOrderNo(),
        userId = currentUser.userId.toString(),
        sourcePhotoBase64 = base64Image
    )
    return FaceSetupPreparation.Ready(
        imageFile = imageFile,
        base64Image = base64Image,
        request = request
    )
}
