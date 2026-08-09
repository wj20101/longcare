package com.ytone.longcare.features.identification.data

import android.content.Context
import androidx.core.net.toUri
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.identification.domain.SetupFaceGateway
import com.ytone.longcare.features.identification.domain.SetupFaceServerResult
import com.ytone.longcare.features.identification.domain.SetupFaceUploadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class SetupFaceGatewayImpl @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val cosRepository: CosRepository,
    private val identificationRepository: IdentificationRepository,
    private val userSessionRepository: UserSessionRepository,
    private val faceDataSource: IdentificationFaceDataSource,
) : SetupFaceGateway {

    override suspend fun uploadFaceImage(imageFile: File): SetupFaceUploadResult {
        val uploadParams = CosUtils.createUploadParams(
            context = applicationContext,
            fileUri = imageFile.toUri(),
            folderType = CosConstants.DEFAULT_FACE_TYPE,
        )
        val uploadResult = cosRepository.uploadFile(uploadParams)
        val uploadedKey = uploadResult.key
        return if (!uploadResult.success || uploadedKey == null) {
            SetupFaceUploadResult.Error(uploadResult.errorMessage ?: "人脸图片上传失败，请稍后重试")
        } else {
            SetupFaceUploadResult.Success(uploadedKey = uploadedKey)
        }
    }

    override suspend fun setFaceOnServer(
        base64Image: String,
        uploadedKey: String,
    ): SetupFaceServerResult {
        val setFaceResult = identificationRepository.setFace(
            SetFaceParamModel(
                faceImg = base64Image,
                faceImgUrl = uploadedKey,
            ),
        )

        return when (setFaceResult) {
            is ApiResult.Success -> SetupFaceServerResult.Success
            is ApiResult.Failure -> SetupFaceServerResult.Error("服务器更新失败: ${setFaceResult.message}")
            is ApiResult.Exception -> SetupFaceServerResult.Error("网络请求异常: ${setFaceResult.exception.message}")
        }
    }

    override suspend fun cacheUserFace(
        userId: Int,
        base64Image: String,
    ): Boolean {
        return faceDataSource.writeUserFaceBase64(userId, base64Image)
    }

    override suspend fun refreshCurrentUserSession() {
        val sessionState = userSessionRepository.sessionState.value
        if (sessionState is SessionState.LoggedIn) {
            userSessionRepository.updateUser(sessionState.user)
        }
    }
}
