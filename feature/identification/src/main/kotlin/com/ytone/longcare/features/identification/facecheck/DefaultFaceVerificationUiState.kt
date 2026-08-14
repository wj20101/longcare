package com.ytone.longcare.features.identification.facecheck

sealed interface DefaultFaceVerificationUiState {
    data class Capturing(val attempt: Int = 0) : DefaultFaceVerificationUiState

    data object ProcessingImage : DefaultFaceVerificationUiState

    data object Verifying : DefaultFaceVerificationUiState

    data object Success : DefaultFaceVerificationUiState

    data class Error(val message: String) : DefaultFaceVerificationUiState
}
