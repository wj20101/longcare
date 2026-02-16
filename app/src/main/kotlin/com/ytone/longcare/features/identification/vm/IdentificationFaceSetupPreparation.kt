package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.models.protos.User
import java.io.File

internal sealed interface FaceSetupPreparation {
    data class Ready(
        val imageFile: File,
        val base64Image: String,
        val request: FaceVerificationRequest,
    ) : FaceSetupPreparation

    data class Error(val message: String) : FaceSetupPreparation
}

internal suspend fun prepareFaceSetupVerificationInput(
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    currentUser: User?,
): FaceSetupPreparation {
    val imageFile = File(imagePath)
    if (!imageFile.exists()) {
        return FaceSetupPreparation.Error("图片文件不存在: $imagePath")
    }

    val base64Image = faceDataSource.imageFileToBase64(imageFile)
    if (currentUser == null) {
        return FaceSetupPreparation.Error("无法获取用户信息")
    }

    if (currentUser.userName.isBlank() || currentUser.identityCardNumber.isBlank()) {
        return FaceSetupPreparation.Error("用户信息不完整，无法进行人脸验证")
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
