package com.ytone.longcare.features.identification.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import com.ytone.longcare.model.OrderInfoRequestModel
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationEvent
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
internal fun IdentificationScreenEffects(
    actions: IdentificationActions,
    orderKey: OrderKey,
    orderInfoRequest: OrderInfoRequestModel,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    identificationViewModel: IdentificationViewModel,
    capturedImageUri: String?,
    faceImagePath: String?,
    faceVerificationState: FaceVerificationState,
    currentVerificationType: VerificationType?,
    photoUploadState: PhotoUploadState,
    context: Context,
) {
    LaunchedEffect(capturedImageUri) {
        capturedImageUri?.let { uriString ->
            identificationViewModel.processElderPhoto(uriString.toUri(), orderInfoRequest)
            actions.clearCapturedImageUri()
        }
    }

    LaunchedEffect(orderInfoRequest.orderId) {
        sharedOrderDetailViewModel.getOrderInfo(orderInfoRequest)
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
                        if (orderInfoRequest.orderId > 0) {
                            identificationViewModel.updateFaceVerificationStatus(
                                request = orderInfoRequest,
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

    LaunchedEffect(Unit) {
        identificationViewModel.events.collect { event ->
            when (event) {
                IdentificationEvent.NavigateToFaceCapture -> actions.onNavigateToManualFaceCapture()
                is IdentificationEvent.ShowToast -> identificationViewModel.showToast(event.message)
            }
        }
    }

    LaunchedEffect(faceImagePath) {
        faceImagePath?.let { imagePath ->
            identificationViewModel.handleFaceCaptureResult(context, imagePath)
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
