package com.ytone.longcare.features.identification.ui

import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationScreenUiState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType

internal enum class IdentificationPersonType {
    SERVICE_PERSON,
    ELDER,
}

internal enum class IdentificationCardStatus {
    ACTION,
    VERIFIED,
    FACE_SETUP_UPLOADING_IMAGE,
    FACE_SETUP_UPDATING_SERVER,
    FACE_SETUP_UPDATING_LOCAL,
    FACE_SETUP_ERROR,
    FACE_INITIALIZING,
    FACE_VERIFYING,
    FACE_ERROR,
    FACE_CANCELLED,
    PHOTO_PROCESSING,
    PHOTO_UPLOADING,
}

internal data class IdentificationCardRenderState(
    val personType: IdentificationPersonType,
    val status: IdentificationCardStatus,
    val actionEnabled: Boolean,
)

internal data class IdentificationScreenRenderState(
    val servicePerson: IdentificationCardRenderState,
    val elder: IdentificationCardRenderState,
    val nextEnabled: Boolean,
)

internal sealed interface IdentificationScreenEvent {
    data object NavigateBack : IdentificationScreenEvent
    data object ContinueToServiceSelection : IdentificationScreenEvent
    data object VerifyServicePerson : IdentificationScreenEvent
    data object CaptureElderPhoto : IdentificationScreenEvent
    data object RetryFaceSetup : IdentificationScreenEvent
    data class RetryFaceVerification(
        val personType: IdentificationPersonType,
    ) : IdentificationScreenEvent
}

internal fun IdentificationScreenUiState.toRenderState(): IdentificationScreenRenderState =
    IdentificationScreenRenderState(
        servicePerson = cardRenderState(IdentificationPersonType.SERVICE_PERSON),
        elder = cardRenderState(IdentificationPersonType.ELDER),
        nextEnabled = identificationState == IdentificationState.ELDER_VERIFIED,
    )

internal fun IdentificationPersonType.primaryEvent(): IdentificationScreenEvent = when (this) {
    IdentificationPersonType.SERVICE_PERSON -> IdentificationScreenEvent.VerifyServicePerson
    IdentificationPersonType.ELDER -> IdentificationScreenEvent.CaptureElderPhoto
}

internal fun IdentificationCardRenderState.retryEvent(): IdentificationScreenEvent? = when (status) {
    IdentificationCardStatus.FACE_SETUP_ERROR -> IdentificationScreenEvent.RetryFaceSetup
    IdentificationCardStatus.FACE_ERROR,
    IdentificationCardStatus.FACE_CANCELLED,
    -> IdentificationScreenEvent.RetryFaceVerification(personType)

    else -> null
}

private fun IdentificationScreenUiState.cardRenderState(
    personType: IdentificationPersonType,
): IdentificationCardRenderState {
    val isVerified = when (personType) {
        IdentificationPersonType.SERVICE_PERSON ->
            identificationState.ordinal >= IdentificationState.SERVICE_VERIFIED.ordinal

        IdentificationPersonType.ELDER ->
            identificationState.ordinal >= IdentificationState.ELDER_VERIFIED.ordinal
    }
    val isCurrentlyVerifying = currentVerificationType == personType.verificationType
    val status = when {
        isVerified -> IdentificationCardStatus.VERIFIED
        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UploadingImage ->
            IdentificationCardStatus.FACE_SETUP_UPLOADING_IMAGE

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UpdatingServer ->
            IdentificationCardStatus.FACE_SETUP_UPDATING_SERVER

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.UpdatingLocal ->
            IdentificationCardStatus.FACE_SETUP_UPDATING_LOCAL

        personType == IdentificationPersonType.SERVICE_PERSON &&
            faceSetupState is FaceSetupState.Error -> IdentificationCardStatus.FACE_SETUP_ERROR

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Initializing ->
            IdentificationCardStatus.FACE_INITIALIZING

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Verifying ->
            IdentificationCardStatus.FACE_VERIFYING

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Error ->
            IdentificationCardStatus.FACE_ERROR

        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Cancelled ->
            IdentificationCardStatus.FACE_CANCELLED

        personType == IdentificationPersonType.ELDER &&
            photoUploadState is PhotoUploadState.Processing ->
            IdentificationCardStatus.PHOTO_PROCESSING

        personType == IdentificationPersonType.ELDER &&
            photoUploadState is PhotoUploadState.Uploading ->
            IdentificationCardStatus.PHOTO_UPLOADING

        else -> IdentificationCardStatus.ACTION
    }
    val actionEnabled = when (personType) {
        IdentificationPersonType.SERVICE_PERSON -> true
        IdentificationPersonType.ELDER ->
            identificationState == IdentificationState.SERVICE_VERIFIED
    }
    return IdentificationCardRenderState(
        personType = personType,
        status = status,
        actionEnabled = actionEnabled,
    )
}

private val IdentificationPersonType.verificationType: VerificationType
    get() = when (this) {
        IdentificationPersonType.SERVICE_PERSON -> VerificationType.SERVICE_PERSON
        IdentificationPersonType.ELDER -> VerificationType.ELDER
    }
