package com.ytone.longcare.domain.faceauth

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

sealed interface FaceSetupRequestResult {
    data class Ready(val request: FaceVerificationRequest) : FaceSetupRequestResult
    data object SessionUnavailable : FaceSetupRequestResult
    data object IdentityIncomplete : FaceSetupRequestResult
}

/** Builds a purpose-specific real-name SDK request without exposing the full session payload. */
interface FaceSetupRequestRepository {
    fun createFaceSetupRequest(orderNo: String, sourcePhotoBase64: String): FaceSetupRequestResult
}
