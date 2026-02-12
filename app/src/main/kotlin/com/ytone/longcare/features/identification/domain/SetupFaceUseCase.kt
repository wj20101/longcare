package com.ytone.longcare.features.identification.domain

import android.content.Context
import androidx.core.net.toUri
import com.ytone.longcare.api.request.SetFaceParamModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

sealed interface SetupFaceResult {
    data object Success : SetupFaceResult

    data class Error(val message: String) : SetupFaceResult
}

class SetupFaceUseCase @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val cosRepository: CosRepository,
    private val identificationRepository: IdentificationRepository,
    private val userSessionRepository: UserSessionRepository,
    private val faceDataSource: IdentificationFaceDataSource,
) {
    suspend fun execute(
        imageFile: File,
        base64Image: String,
        currentUser: User?,
    ): SetupFaceResult {
        if (currentUser == null) {
            return SetupFaceResult.Error("更新本地用户数据失败：用户信息为空")
        }

        val uploadParams = CosUtils.createUploadParams(
            context = applicationContext,
            fileUri = imageFile.toUri(),
            folderType = CosConstants.DEFAULT_FACE_TYPE,
        )

        val uploadResult = cosRepository.uploadFile(uploadParams)
        if (!uploadResult.success || uploadResult.key == null) {
            return SetupFaceResult.Error(uploadResult.errorMessage ?: "图片上传失败")
        }

        val setFaceResult = identificationRepository.setFace(
            SetFaceParamModel(
                faceImg = base64Image,
                faceImgUrl = uploadResult.key,
            ),
        )

        return when (setFaceResult) {
            is ApiResult.Success -> {
                faceDataSource.writeUserFaceBase64(currentUser.userId, base64Image)
                userSessionRepository.updateUser(currentUser)
                SetupFaceResult.Success
            }

            is ApiResult.Failure -> {
                SetupFaceResult.Error("服务器更新失败: ${setFaceResult.message}")
            }

            is ApiResult.Exception -> {
                SetupFaceResult.Error("网络请求异常: ${setFaceResult.exception.message}")
            }
        }
    }
}
