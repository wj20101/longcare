package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest

/**
 * 身份认证状态枚举
 */
enum class IdentificationState {
    INITIAL,
    SERVICE_VERIFIED,
    ELDER_VERIFIED,
}

enum class VerificationType {
    SERVICE_PERSON,
    ELDER,
}

sealed class FaceVerificationState {
    data object Idle : FaceVerificationState()

    data object Initializing : FaceVerificationState()

    data object Verifying : FaceVerificationState()

    data class Success(val result: FaceVerifyResult) : FaceVerificationState()

    data class Error(val error: FaceVerifyError?, val message: String) : FaceVerificationState()

    data object Cancelled : FaceVerificationState()
}

sealed class PhotoUploadState {
    data object Initial : PhotoUploadState()

    data object Processing : PhotoUploadState()

    data object Uploading : PhotoUploadState()

    data object Success : PhotoUploadState()

    data class Error(val message: String) : PhotoUploadState()
}

sealed class FaceSetupState {
    data object Initial : FaceSetupState()

    data object UploadingImage : FaceSetupState()

    data object UpdatingServer : FaceSetupState()

    data object UpdatingLocal : FaceSetupState()

    data object Success : FaceSetupState()

    data class Error(val message: String) : FaceSetupState()
}

data class IdentificationScreenUiState(
    val identificationState: IdentificationState = IdentificationState.INITIAL,
    val currentVerificationType: VerificationType? = null,
    val faceVerificationState: FaceVerificationState = FaceVerificationState.Idle,
    val photoUploadState: PhotoUploadState = PhotoUploadState.Initial,
    val faceSetupState: FaceSetupState = FaceSetupState.Initial,
    val pendingUiActions: List<IdentificationUiAction> = emptyList(),
    val faceSdkLaunchRequest: IdentificationFaceSdkLaunchRequest? = null,
)
