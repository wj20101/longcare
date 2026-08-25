package com.ytone.longcare.features.identification.data

import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDataGateway
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull

private const val FACE_STATE_LOOKUP_TIMEOUT_MS = 10_000L

class VerifyServicePersonDataGatewayImpl @Inject constructor(
    private val faceCacheCleaner: FaceCacheCleaner,
    private val identificationRepository: IdentificationRepository,
) : VerifyServicePersonDataGateway {

    override suspend fun resolveFaceSource(): ServicePersonFaceSource {
        val faceResult = withTimeoutOrNull(FACE_STATE_LOOKUP_TIMEOUT_MS) {
            identificationRepository.getFace()
        }
        return when (faceResult) {
            null -> {
                FaceVerificationEventTracker.trackError(
                    eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
                    extras = mapOf(
                        "reason" to "total_timeout",
                        "timeoutMs" to FACE_STATE_LOOKUP_TIMEOUT_MS,
                    ),
                )
                ServicePersonFaceSource.RequireFaceSetup
            }

            is ApiResult.Success -> {
                val url = faceResult.data.faceImgUrl
                if (url.isBlank()) {
                    ServicePersonFaceSource.RequireFaceSetup
                } else {
                    ServicePersonFaceSource.RegisteredFaceAvailable
                }
            }

            is ApiResult.Failure -> {
                if (SessionInvalidationCode.requiresLogout(faceResult.code)) {
                    ServicePersonFaceSource.SessionInvalidated
                } else {
                    FaceVerificationEventTracker.trackError(
                        eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
                        extras = mapOf(
                            "apiCode" to faceResult.code,
                            "apiMessage" to faceResult.message,
                        ),
                    )
                    ServicePersonFaceSource.RequireFaceSetup
                }
            }

            is ApiResult.Exception -> {
                FaceVerificationEventTracker.trackError(
                    eventType = EventType.SERVICE_FACE_SOURCE_ERROR,
                    throwable = faceResult.exception,
                    extras = mapOf("message" to faceResult.exception.message),
                )
                ServicePersonFaceSource.RequireFaceSetup
            }
        }
    }

    override suspend fun clearLocalFaceArtifacts(userId: Int) {
        faceCacheCleaner.clearUserFaceArtifacts(userId)
    }
}
