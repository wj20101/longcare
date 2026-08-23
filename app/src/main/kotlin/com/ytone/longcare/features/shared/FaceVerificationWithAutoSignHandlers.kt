package com.ytone.longcare.features.shared

import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

internal fun startAutoSignVerification(
    sourcePhotoBase64: String?,
    currentUserId: String?,
    viewModel: FaceVerificationViewModel,
    copy: FaceAutoSignCopy,
    onShowMessage: (String) -> Unit,
) {
    val sourcePhoto = sourcePhotoBase64
    if (sourcePhoto == null) {
        onShowMessage(copy.photoRequired)
        return
    }
    if (currentUserId.isNullOrBlank()) {
        onShowMessage(copy.userUnavailable)
        return
    }
    viewModel.startFaceVerificationWithAutoSign(
        orderNo = "order_${System.currentTimeMillis()}",
        userId = currentUserId,
        sourcePhotoStr = sourcePhoto
    )
}

internal fun consumeFaceVerifyUiState(
    uiState: FaceVerificationViewModel.FaceVerifyUiState,
    copy: FaceAutoSignCopy,
    onShowMessage: (String) -> Unit,
    onVerificationSuccess: (FaceVerifyResult) -> Unit
) {
    when (uiState) {
        is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
            onShowMessage(copy.success)
            onVerificationSuccess(uiState.result)
        }

        is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
            onShowMessage(uiState.message)
        }

        is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
            onShowMessage(copy.cancelled)
        }

        else -> Unit
    }
}

internal data class FaceAutoSignCopy(
    val photoRequired: String,
    val userUnavailable: String,
    val success: String,
    val cancelled: String,
)
