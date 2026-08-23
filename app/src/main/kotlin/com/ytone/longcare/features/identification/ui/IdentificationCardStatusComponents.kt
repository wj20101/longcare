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
    personType: IdentificationPersonType,
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
        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UploadingImage -> {
            LoadingStatusRow(text = stringResource(R.string.identification_uploading_image))
        }

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UpdatingServer -> {
            LoadingStatusRow(text = stringResource(R.string.identification_updating_server))
        }

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UpdatingLocal -> {
            LoadingStatusRow(text = stringResource(R.string.identification_updating_local))
        }

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.Error -> {
            RetryStatusColumn(
                statusText = stringResource(R.string.identification_setup_failed),
                statusColor = Color(0xFFFF3B30),
                buttonText = stringResource(R.string.common_retry),
                onClick = onRetryFaceSetup
            )
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Initializing -> {
            LoadingStatusRow(text = stringResource(R.string.common_initializing))
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Verifying -> {
            LoadingStatusRow(
                text = stringResource(
                    R.string.identification_recognizing,
                    stringResource(personType.labelRes),
                ),
            )
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Error -> {
            RetryStatusColumn(
                statusText = stringResource(R.string.identification_verification_failed),
                statusColor = Color(0xFFFF3B30),
                buttonText = stringResource(R.string.common_retry),
                onClick = onRetryFaceVerification
            )
        }

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Cancelled -> {
            RetryStatusColumn(
                statusText = stringResource(R.string.identification_cancelled),
                statusColor = Color(0xFF666666),
                buttonText = stringResource(R.string.identification_retry),
                onClick = onRetryFaceVerification
            )
        }

        else -> {
            val isButtonEnabled = when {
                personType == IdentificationPersonType.SERVICE_PERSON -> true
                personType == IdentificationPersonType.ELDER &&
                    identificationState == IdentificationState.SERVICE_VERIFIED -> true
                else -> false
            }

            val isProcessing = personType == IdentificationPersonType.ELDER && (
                photoUploadState is PhotoUploadState.Processing ||
                    photoUploadState is PhotoUploadState.Uploading
                )

            if (isProcessing) {
                val statusText = if (photoUploadState is PhotoUploadState.Uploading) {
                    stringResource(R.string.identification_uploading)
                } else {
                    stringResource(R.string.common_processing)
                }
                LoadingStatusRow(text = statusText)
            } else {
                PrimaryActionButton(
                    text = if (personType == IdentificationPersonType.ELDER) {
                        stringResource(R.string.identification_elder_photo_action)
                    } else {
                        stringResource(
                            R.string.identification_action,
                            stringResource(personType.labelRes),
                        )
                    },
                    enabled = isButtonEnabled,
                    textSize = 12.sp,
                    onClick = onVerifyClick
                )
            }
        }
    }
}
