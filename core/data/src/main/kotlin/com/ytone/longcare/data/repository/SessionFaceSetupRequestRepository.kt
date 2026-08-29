package com.ytone.longcare.data.repository

import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.domain.faceauth.FaceSetupRequestRepository
import com.ytone.longcare.domain.faceauth.FaceSetupRequestResult
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionFaceSetupRequestRepository @Inject constructor(
    private val sessionSecretProvider: SessionSecretProvider,
) : FaceSetupRequestRepository {
    override fun createFaceSetupRequest(
        orderNo: String,
        sourcePhotoBase64: String,
    ): FaceSetupRequestResult {
        val identity = sessionSecretProvider.faceSetupIdentity()
            ?: return FaceSetupRequestResult.SessionUnavailable
        if (identity.userName.isBlank() || identity.identityCardNumber.isBlank()) {
            return FaceSetupRequestResult.IdentityIncomplete
        }
        return FaceSetupRequestResult.Ready(
            FaceVerificationRequest(
                name = identity.userName,
                idNo = identity.identityCardNumber,
                orderNo = orderNo,
                userId = identity.userId.toString(),
                sourcePhotoStr = sourcePhotoBase64,
            )
        )
    }
}
