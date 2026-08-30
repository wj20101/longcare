package com.ytone.longcare.features.identification.ui

import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLauncher
import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationUiAction
import com.ytone.longcare.features.identification.vm.IdentificationUiEffect
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType

internal fun consumeStringResult(
    result: String?,
    onResult: (String) -> Unit,
    acknowledge: () -> Unit,
): Boolean {
    result ?: return false
    onResult(result)
    acknowledge()
    return true
}

internal fun consumeDefaultFaceVerificationResult(
    result: Boolean?,
    onVerified: () -> Unit,
    onReset: () -> Unit,
    acknowledge: () -> Unit,
): Boolean {
    result ?: return false
    if (result) onVerified()
    onReset()
    acknowledge()
    return true
}

internal fun dispatchPendingUiAction(
    action: IdentificationUiAction?,
    onDefaultFaceVerification: (IdentificationUiEffect.NavigateToDefaultFaceVerification) -> Unit,
    onManualFaceCapture: (IdentificationUiEffect.NavigateToFaceCapture) -> Unit,
    onMessage: (IdentificationUiEffect.ShowMessage) -> Unit,
    acknowledge: (Long) -> Unit,
): Boolean {
    action ?: return false
    when (val effect = action.effect) {
        is IdentificationUiEffect.NavigateToDefaultFaceVerification ->
            onDefaultFaceVerification(effect)

        is IdentificationUiEffect.NavigateToFaceCapture -> onManualFaceCapture(effect)
        is IdentificationUiEffect.ShowMessage -> onMessage(effect)
    }
    acknowledge(action.id)
    return true
}

internal fun handleFaceVerificationCompletion(
    state: FaceVerificationState,
    verificationType: VerificationType?,
    onServicePersonVerified: () -> Unit,
    onElderVerified: () -> Unit,
    onPersistElderVerification: () -> Unit,
): Boolean {
    if (state !is FaceVerificationState.Success) return false
    when (verificationType) {
        VerificationType.SERVICE_PERSON -> onServicePersonVerified()
        VerificationType.ELDER -> {
            onElderVerified()
            onPersistElderVerification()
        }

        null -> return false
    }
    return true
}

internal fun handlePhotoUploadCompletion(
    state: PhotoUploadState,
    onSuccess: () -> Unit,
    onReset: () -> Unit,
): Boolean = when (state) {
    PhotoUploadState.Success -> {
        onSuccess()
        onReset()
        true
    }

    is PhotoUploadState.Error -> {
        onReset()
        true
    }

    else -> false
}

internal suspend fun deliverFaceSdkLaunchRequest(
    request: IdentificationFaceSdkLaunchRequest?,
    launcher: IdentificationFaceSdkLauncher,
    onEvent: (Long, com.ytone.longcare.common.faceauth.FaceSdkEvent) -> Unit,
    acknowledge: (Long) -> Unit,
): Boolean {
    request ?: return false
    launcher.launch(request) { event -> onEvent(request.id, event) }
    acknowledge(request.id)
    return true
}
