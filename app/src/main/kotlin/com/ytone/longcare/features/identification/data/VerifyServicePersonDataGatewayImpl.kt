package com.ytone.longcare.features.identification.data

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDataGateway
import javax.inject.Inject

class VerifyServicePersonDataGatewayImpl @Inject constructor(
    private val faceDataSource: IdentificationFaceDataSource,
    private val identificationRepository: IdentificationRepository,
) : VerifyServicePersonDataGateway {

    override suspend fun readCachedFace(userId: Int): String? {
        return faceDataSource.readUserFaceBase64(userId)
    }

    override suspend fun resolveFaceSource(): ServicePersonFaceSource {
        return when (val faceResult = identificationRepository.getFace()) {
            is ApiResult.Success -> {
                val url = faceResult.data.faceImgUrl
                if (url.isBlank()) {
                    ServicePersonFaceSource.RequireFaceSetup
                } else {
                    ServicePersonFaceSource.RemoteFace(sourcePhotoUrl = url)
                }
            }

            is ApiResult.Failure -> {
                if (faceResult.code == SUCCESS_CODE) {
                    ServicePersonFaceSource.RequireFaceSetup
                } else {
                    ServicePersonFaceSource.Error(faceResult.message)
                }
            }

            is ApiResult.Exception -> {
                ServicePersonFaceSource.Error("网络异常: ${faceResult.exception.message}")
            }
        }
    }

    private companion object {
        private const val SUCCESS_CODE = 1000
    }
}
