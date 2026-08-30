package com.ytone.longcare.features.identification.api

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

data class IdentificationFaceSdkLaunchRequest(
    val id: Long,
    val config: FaceVerificationConfig,
    val request: FaceVerificationRequest,
)

fun interface IdentificationFaceSdkLauncher {
    suspend fun launch(
        request: IdentificationFaceSdkLaunchRequest,
        onEvent: (FaceSdkEvent) -> Unit,
    )
}
