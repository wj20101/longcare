package com.ytone.longcare.features.identification.domain

import java.io.File
import javax.inject.Inject

sealed interface SetupFaceResult {
    data object Success : SetupFaceResult

    data class Error(val message: String) : SetupFaceResult
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
            return SetupFaceResult.Error("更新本地用户数据失败：用户信息为空")
        }

        val uploadResult = gateway.uploadFaceImage(imageFile)
        if (uploadResult is SetupFaceUploadResult.Error) {
            return SetupFaceResult.Error(uploadResult.message)
        }
        val uploadedKey = (uploadResult as SetupFaceUploadResult.Success).uploadedKey

        return when (val setFaceResult = gateway.setFaceOnServer(base64Image, uploadedKey)) {
            SetupFaceServerResult.Success -> {
                // Cache only after the server accepts the face to keep local state behind server truth.
                if (!gateway.cacheUserFace(currentUserId, base64Image)) {
                    return SetupFaceResult.Error("本地人脸缓存失败，请重试")
                }
                gateway.refreshCurrentUserSession()
                SetupFaceResult.Success
            }

            is SetupFaceServerResult.Error -> SetupFaceResult.Error(setFaceResult.message)
        }
    }
}
