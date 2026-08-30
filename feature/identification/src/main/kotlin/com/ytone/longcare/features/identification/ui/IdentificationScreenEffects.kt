package com.ytone.longcare.features.identification.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLauncher
import com.ytone.longcare.features.identification.vm.IdentificationScreenUiState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
internal fun IdentificationScreenEffects(
    actions: IdentificationActions,
    orderKey: OrderKey,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    identificationViewModel: IdentificationViewModel,
    faceSdkLauncher: IdentificationFaceSdkLauncher,
    screenUiState: IdentificationScreenUiState,
    capturedImageUri: String?,
    faceImagePath: String?,
    defaultFaceVerificationResult: Boolean?,
    context: Context,
) {
    val currentActions by rememberUpdatedState(actions)
    val currentFaceSdkLauncher by rememberUpdatedState(faceSdkLauncher)

    LaunchedEffect(screenUiState.faceSdkLaunchRequest?.id) {
        deliverFaceSdkLaunchRequest(
            request = screenUiState.faceSdkLaunchRequest,
            launcher = currentFaceSdkLauncher,
            onEvent = identificationViewModel::onFaceSdkEvent,
            acknowledge = identificationViewModel::consumeFaceSdkLaunchRequest,
        )
    }

    LaunchedEffect(capturedImageUri) {
        consumeStringResult(
            result = capturedImageUri,
            onResult = { uriString ->
                identificationViewModel.processElderPhoto(uriString.toUri(), orderKey)
            },
            acknowledge = currentActions.clearCapturedImageUri,
        )
    }

    LaunchedEffect(faceImagePath) {
        consumeStringResult(
            result = faceImagePath,
            onResult = identificationViewModel::handleFaceCaptureResult,
            acknowledge = currentActions.clearFaceImagePath,
        )
    }

    LaunchedEffect(defaultFaceVerificationResult) {
        consumeDefaultFaceVerificationResult(
            result = defaultFaceVerificationResult,
            onVerified = {
                identificationViewModel.setServicePersonVerified()
                identificationViewModel.updateFaceVerificationStatus(orderKey, verified = true)
            },
            onReset = identificationViewModel::resetFaceVerificationState,
            acknowledge = currentActions.clearDefaultFaceVerificationResult,
        )
    }

    LaunchedEffect(orderKey) {
        sharedOrderDetailViewModel.getOrderInfo(orderKey)
    }

    LaunchedEffect(
        screenUiState.faceVerificationState,
        screenUiState.currentVerificationType,
    ) {
        handleFaceVerificationCompletion(
            state = screenUiState.faceVerificationState,
            verificationType = screenUiState.currentVerificationType,
            onServicePersonVerified = identificationViewModel::setServicePersonVerified,
            onElderVerified = identificationViewModel::setElderVerified,
            onPersistElderVerification = {
                if (orderKey.orderId > 0) {
                    identificationViewModel.updateFaceVerificationStatus(orderKey, verified = true)
                }
            },
        )
    }

    val pendingAction = screenUiState.pendingUiActions.firstOrNull()
    LaunchedEffect(pendingAction?.id) {
        dispatchPendingUiAction(
            action = pendingAction,
            onDefaultFaceVerification = { effect ->
                currentActions.onNavigateToDefaultFaceVerification(effect.orderKey)
            },
            onManualFaceCapture = { effect ->
                Toast.makeText(
                    context,
                    context.getString(effect.messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
                currentActions.onNavigateToManualFaceCapture()
            },
            onMessage = { effect ->
                val duration = if (effect.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(context, effect.message, duration).show()
            },
            acknowledge = identificationViewModel::consumeUiAction,
        )
    }

    LaunchedEffect(screenUiState.photoUploadState) {
        handlePhotoUploadCompletion(
            state = screenUiState.photoUploadState,
            onSuccess = { currentActions.onNavigateToSelectService(orderKey) },
            onReset = identificationViewModel::resetPhotoUploadState,
        )
    }
}
