package com.ytone.longcare.features.identification.data

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDataGateway
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
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
                    FaceVerificationEventTracker.trackError(
                        eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
                        extras = mapOf(
                            "apiCode" to faceResult.code,
                            "apiMessage" to faceResult.message,
                        ),
                    )
                    ServicePersonFaceSource.Rejected(
                        faceResult.message.takeIf(String::isNotBlank),
                    )
                }
            }

            is ApiResult.Exception -> {
                FaceVerificationEventTracker.trackError(
                    eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
                    throwable = faceResult.exception,
                    extras = mapOf("message" to faceResult.exception.message),
                )
                ServicePersonFaceSource.NetworkError
            }
        }
    }

    private companion object {
        private const val SUCCESS_CODE = 1000
    }
}
