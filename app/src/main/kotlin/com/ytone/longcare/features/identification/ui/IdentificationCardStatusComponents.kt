package com.ytone.longcare.features.identification.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.PhotoUploadState

@Composable
internal fun IdentificationCardStatusArea(
    personType: String,
    isVerified: Boolean,
    isCurrentlyVerifying: Boolean,
    identificationState: IdentificationState,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState,
    faceSetupState: FaceSetupState,
    onVerifyClick: () -> Unit,
    onRetryFaceSetup: () -> Unit,
    onRetryFaceVerification: () -> Unit
) {
    if (isVerified) {
        VerifiedStatusRow(personType = personType)
        return
    }

    when {
        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UploadingImage -> {
            LoadingStatusRow(text = "上传图片中...")
        }

        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UpdatingServer -> {
            LoadingStatusRow(text = "更新服务器...")
        }

        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UpdatingLocal -> {
            LoadingStatusRow(text = "更新本地数据...")
        }

        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.Error -> {
            RetryStatusColumn(
                statusText = "设置失败",
                statusColor = Color(0xFFFF3B30),
                buttonText = "重试",
                onClick = onRetryFaceSetup
            )
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Initializing -> {
            LoadingStatusRow(text = "初始化中...")
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Verifying -> {
            LoadingStatusRow(text = "${personType}识别中...")
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Error -> {
            RetryStatusColumn(
                statusText = "验证失败",
                statusColor = Color(0xFFFF3B30),
                buttonText = "重试",
                onClick = onRetryFaceVerification
            )
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Cancelled -> {
            RetryStatusColumn(
                statusText = "已取消",
                statusColor = Color(0xFF666666),
                buttonText = "重新识别",
                onClick = onRetryFaceVerification
            )
        }

        else -> {
            val isButtonEnabled = when {
                personType == IdentificationConstants.SERVICE_PERSON -> true
                personType == IdentificationConstants.ELDER && identificationState == IdentificationState.SERVICE_VERIFIED -> true
                else -> false
            }

            val isProcessing = personType == IdentificationConstants.ELDER && (
                photoUploadState is PhotoUploadState.Processing ||
                    photoUploadState is PhotoUploadState.Uploading
                )

            if (isProcessing) {
                val statusText = if (photoUploadState is PhotoUploadState.Uploading) {
                    "上传中..."
                } else {
                    "处理中..."
                }
                LoadingStatusRow(text = statusText)
            } else {
                PrimaryActionButton(
                    text = if (personType == IdentificationConstants.ELDER) {
                        stringResource(R.string.identification_elder_photo_action)
                    } else {
                        "进行${personType}识别"
                    },
                    enabled = isButtonEnabled,
                    textSize = 12.sp,
                    onClick = onVerifyClick
                )
            }
        }
    }
}
