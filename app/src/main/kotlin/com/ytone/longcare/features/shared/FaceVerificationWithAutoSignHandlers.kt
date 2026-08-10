package com.ytone.longcare.features.shared

import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

internal fun startAutoSignVerification(
    sourcePhotoBase64: String?,
    currentUserId: String?,
    viewModel: FaceVerificationViewModel,
    onShowMessage: (String) -> Unit
) {
    val sourcePhoto = sourcePhotoBase64
    if (sourcePhoto == null) {
        onShowMessage("请先拍摄人脸照片")
        return
    }
    if (currentUserId.isNullOrBlank()) {
        onShowMessage("用户信息不可用，请重新登录后重试")
        return
    }
    viewModel.startFaceVerificationWithAutoSign(
        orderNo = "order_${System.currentTimeMillis()}",
        userId = currentUserId,
        sourcePhotoStr = sourcePhoto
    )
}

internal fun resolveFaceCaptureErrorMessage(error: Exception): String {
    return when (error.message) {
        "图片文件不存在", "图片处理失败" -> error.message ?: "图片处理失败"
        else -> "图片处理失败: ${error.message ?: "请重新拍摄后重试"}"
    }
}

internal fun consumeFaceVerifyUiState(
    uiState: FaceVerificationViewModel.FaceVerifyUiState,
    onShowMessage: (String) -> Unit,
    onVerificationSuccess: (FaceVerifyResult) -> Unit
) {
    when (uiState) {
        is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
            onShowMessage("人脸验证成功！")
            onVerificationSuccess(uiState.result)
        }

        is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
            onShowMessage(uiState.message)
        }

        is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
            onShowMessage("用户取消了人脸验证")
        }

        else -> Unit
    }
}
