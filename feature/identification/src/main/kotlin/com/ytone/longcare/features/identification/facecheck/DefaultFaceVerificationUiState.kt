package com.ytone.longcare.features.identification.facecheck

import com.ytone.longcare.features.identification.domain.CheckFaceFailure

sealed interface DefaultFaceVerificationUiState {
    data class Capturing(val attempt: Int = 0) : DefaultFaceVerificationUiState

    data object ProcessingImage : DefaultFaceVerificationUiState

    data object Verifying : DefaultFaceVerificationUiState

    data object Success : DefaultFaceVerificationUiState

    data class RetryableError(
        val failure: CheckFaceFailure? = null,
    ) : DefaultFaceVerificationUiState

    data class TerminalError(
        val failure: CheckFaceFailure,
    ) : DefaultFaceVerificationUiState

    data object SessionInvalidated : DefaultFaceVerificationUiState
}
