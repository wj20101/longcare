package com.ytone.longcare.features.identification.vm

import androidx.annotation.StringRes
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType

internal fun buildFaceVerifyErrorMessage(
    textResolver: ResourceTextResolver,
    @StringRes prefixRes: Int,
    error: FaceVerifyError?,
): String {
    val reason =
        error?.description
            ?.takeIf { it.isNotBlank() }
            ?: error?.reason?.takeIf { it.isNotBlank() }
            ?: textResolver.text(R.string.identification_retry_later)
    return textResolver.text(
        R.string.identification_face_error_with_reason,
        textResolver.text(prefixRes),
        reason,
    )
}

internal fun handleIdentificationFlowFaceSdkEvent(
    event: FaceSdkEvent,
    currentVerificationType: () -> VerificationType?,
    setVerificationState: (FaceVerificationState) -> Unit,
    onSetFaceVerificationError: (String, FaceVerifyError?, EventType) -> Unit,
    onServicePersonVerified: () -> Unit,
    onElderVerified: () -> Unit,
    showToast: (String) -> Unit,
    textResolver: ResourceTextResolver,
): Unit = when (event) {
        FaceSdkEvent.InitSuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast(textResolver.text(R.string.identification_face_init_success))
            setVerificationState(FaceVerificationState.Verifying)
        }
        is FaceSdkEvent.InitFailed -> onSetFaceVerificationError(
            buildFaceVerifyErrorMessage(
                textResolver,
                R.string.identification_face_recognition_init_failed,
                event.error,
            ),
            event.error,
            EventType.FACE_INIT_ERROR,
        )
        is FaceSdkEvent.VerifySuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_SUCCESS,
                extras = mapOf(
                    "verificationType" to currentVerificationType(),
                    "isSuccess" to event.result.isSuccess,
                ),
            )
            showToast(textResolver.text(R.string.identification_face_verification_success))
            setVerificationState(FaceVerificationState.Success(event.result))

            when (currentVerificationType()) {
                VerificationType.SERVICE_PERSON -> {
                    onServicePersonVerified()
                    showToast(textResolver.text(R.string.identification_service_person_verified))
                }

                VerificationType.ELDER -> {
                    onElderVerified()
                    showToast(textResolver.text(R.string.identification_elder_verified))
                }

                null -> {
                    showToast(textResolver.text(R.string.identification_unknown_verification_type))
                }
            }
        }
        is FaceSdkEvent.VerifyFailed -> onSetFaceVerificationError(
            buildFaceVerifyErrorMessage(
                textResolver,
                R.string.identification_face_verification_failed,
                event.error,
            ),
            event.error,
            EventType.FACE_VERIFY_ERROR,
        )
        FaceSdkEvent.Cancelled -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_CANCELLED,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast(textResolver.text(R.string.identification_face_verification_cancelled))
            setVerificationState(FaceVerificationState.Cancelled)
        }
    }
