package com.ytone.longcare.features.identification.domain

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.model.isSucceed
import com.ytone.longcare.models.protos.User
import javax.inject.Inject

sealed interface VerifyServicePersonDecision {
    data class UseCachedFace(
        val user: User,
        val sourcePhotoBase64: String,
    ) : VerifyServicePersonDecision

    data class DownloadAndCache(
        val user: User,
        val sourcePhotoUrl: String,
    ) : VerifyServicePersonDecision

    data object RequireFaceSetup : VerifyServicePersonDecision

    data class Error(val message: String) : VerifyServicePersonDecision
}

class VerifyServicePersonUseCase @Inject constructor(
    private val faceDataSource: IdentificationFaceDataSource,
    private val identificationRepository: IdentificationRepository,
) {
    suspend fun execute(user: User?): VerifyServicePersonDecision {
        if (user == null) {
            return VerifyServicePersonDecision.Error("无法获取用户信息")
        }

        val cachedBase64 = faceDataSource.readUserFaceBase64(user.userId)
        if (!cachedBase64.isNullOrBlank()) {
            return VerifyServicePersonDecision.UseCachedFace(
                user = user,
                sourcePhotoBase64 = cachedBase64,
            )
        }

        return when (val faceResult = identificationRepository.getFace()) {
            is ApiResult.Success -> {
                val url = faceResult.data.faceImgUrl
                if (url.isBlank()) {
                    VerifyServicePersonDecision.RequireFaceSetup
                } else {
                    VerifyServicePersonDecision.DownloadAndCache(
                        user = user,
                        sourcePhotoUrl = url,
                    )
                }
            }

            is ApiResult.Failure -> {
                if (faceResult.code.isSucceed()) {
                    VerifyServicePersonDecision.RequireFaceSetup
                } else {
                    VerifyServicePersonDecision.Error(faceResult.message)
                }
            }

            is ApiResult.Exception -> {
                VerifyServicePersonDecision.Error("网络异常: ${faceResult.exception.message}")
            }
        }
    }
}
