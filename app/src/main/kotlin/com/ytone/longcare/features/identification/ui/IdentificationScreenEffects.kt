package com.ytone.longcare.features.identification.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationUiAction
import com.ytone.longcare.features.identification.vm.IdentificationUiEffect
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
internal fun IdentificationScreenEffects(
    actions: IdentificationActions,
    orderKey: OrderKey,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    identificationViewModel: IdentificationViewModel,
    capturedImageUri: String?,
    faceImagePath: String?,
    faceVerificationState: FaceVerificationState,
    currentVerificationType: VerificationType?,
    photoUploadState: PhotoUploadState,
    pendingUiActions: List<IdentificationUiAction>,
    context: Context,
) {
    LaunchedEffect(capturedImageUri) {
        capturedImageUri?.let { uriString ->
            identificationViewModel.processElderPhoto(uriString.toUri(), orderKey)
            actions.clearCapturedImageUri()
        }
    }

    LaunchedEffect(orderKey) {
        sharedOrderDetailViewModel.getOrderInfo(orderKey)
    }

    LaunchedEffect(faceVerificationState, currentVerificationType) {
        when (faceVerificationState) {
            is FaceVerificationState.Success -> {
                when (currentVerificationType) {
                    VerificationType.SERVICE_PERSON -> {
                        identificationViewModel.setServicePersonVerified()
                    }

                    VerificationType.ELDER -> {
                        identificationViewModel.setElderVerified()
                        if (orderKey.orderId > 0) {
                            identificationViewModel.updateFaceVerificationStatus(
                                orderKey = orderKey,
                                verified = true
                            )
                        }
                    }

                    null -> {}
                }
            }

            is FaceVerificationState.Error -> {}
            is FaceVerificationState.Cancelled -> {}
            else -> {}
        }
    }

    val pendingAction = pendingUiActions.firstOrNull()
    LaunchedEffect(pendingAction?.id) {
        pendingAction?.let { action ->
            when (val effect = action.effect) {
                is IdentificationUiEffect.NavigateToDefaultFaceVerification -> {
                    actions.onNavigateToDefaultFaceVerification(effect.orderKey)
                }

                is IdentificationUiEffect.NavigateToFaceCapture -> {
                    Toast.makeText(
                        context,
                        context.getString(effect.messageRes),
                        Toast.LENGTH_SHORT,
                    ).show()
                    actions.onNavigateToManualFaceCapture()
                }

                is IdentificationUiEffect.ShowMessage -> {
                    val duration = if (effect.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    Toast.makeText(context, effect.message, duration).show()
                }
            }
            identificationViewModel.consumeUiAction(action.id)
        }
    }

    LaunchedEffect(faceImagePath) {
        faceImagePath?.let { imagePath ->
            identificationViewModel.handleFaceCaptureResult(imagePath)
            actions.clearFaceImagePath()
        }
    }

    LaunchedEffect(photoUploadState) {
        when (photoUploadState) {
            is PhotoUploadState.Success -> {
                actions.onNavigateToSelectService(orderKey)
                identificationViewModel.resetPhotoUploadState()
            }

            is PhotoUploadState.Error -> {
                identificationViewModel.resetPhotoUploadState()
            }

            else -> {}
        }
    }
}
