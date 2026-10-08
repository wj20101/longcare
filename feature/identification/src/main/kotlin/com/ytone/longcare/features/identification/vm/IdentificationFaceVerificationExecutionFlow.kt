package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

data class IdentificationFaceSdkLaunchRequest(
    val id: Long,
    val config: FaceVerificationConfig,
    val request: FaceVerificationRequest,
)

internal sealed interface IdentificationFaceSdkPurpose {
    data object Standard : IdentificationFaceSdkPurpose
    data class FaceSetup(val ready: FaceSetupPreparation.Ready) : IdentificationFaceSdkPurpose
}
