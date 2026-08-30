package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.features.identification.api.IdentificationFaceSdkLaunchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal fun createIdentificationScreenUiState(
    scope: CoroutineScope,
    identificationState: StateFlow<IdentificationState>,
    currentVerificationType: StateFlow<VerificationType?>,
    faceVerificationState: StateFlow<FaceVerificationState>,
    photoUploadState: StateFlow<PhotoUploadState>,
    faceSetupState: StateFlow<FaceSetupState>,
    pendingUiActions: StateFlow<List<IdentificationUiAction>>,
    faceSdkLaunchRequest: StateFlow<IdentificationFaceSdkLaunchRequest?>,
    started: SharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
): StateFlow<IdentificationScreenUiState> {
    val renderState = combine(
        identificationState,
        currentVerificationType,
        faceVerificationState,
        photoUploadState,
        faceSetupState,
    ) { identity, verificationType, faceVerification, photoUpload, faceSetup ->
        IdentificationScreenUiState(
            identificationState = identity,
            currentVerificationType = verificationType,
            faceVerificationState = faceVerification,
            photoUploadState = photoUpload,
            faceSetupState = faceSetup,
        )
    }

    return combine(
        renderState,
        pendingUiActions,
        faceSdkLaunchRequest,
    ) { state, actions, launchRequest ->
        state.copy(
            pendingUiActions = actions,
            faceSdkLaunchRequest = launchRequest,
        )
    }.stateIn(
        scope = scope,
        started = started,
        initialValue = IdentificationScreenUiState(
            identificationState = identificationState.value,
            currentVerificationType = currentVerificationType.value,
            faceVerificationState = faceVerificationState.value,
            photoUploadState = photoUploadState.value,
            faceSetupState = faceSetupState.value,
            pendingUiActions = pendingUiActions.value,
            faceSdkLaunchRequest = faceSdkLaunchRequest.value,
        ),
    )
}
