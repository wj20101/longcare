package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.domain.SetupFaceResult
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchFaceSetupUpload(
    scope: CoroutineScope,
    setupFaceUseCase: SetupFaceUseCase,
    resolveCurrentUserId: suspend () -> Int?,
    imageFile: File,
    base64Image: String,
    onUploading: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    textResolver: ResourceTextResolver,
) {
    scope.launch {
        try {
            onUploading()
            when (
                val result = setupFaceUseCase.execute(
                    imageFile = imageFile,
                    base64Image = base64Image,
                    currentUserId = resolveCurrentUserId(),
                )
            ) {
                SetupFaceResult.Success -> onSuccess()
                is SetupFaceResult.Error -> onError(textResolver.resolve(result.failure))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(
                textResolver.text(
                    R.string.identification_face_setup_upload_failed,
                    textResolver.text(R.string.identification_retry_later),
                ),
            )
        }
    }
}

internal fun handleStandardFaceSetupSdkEvent(
    event: FaceSdkEvent,
    ready: FaceSetupPreparation.Ready,
    scope: CoroutineScope,
    setupFaceUseCase: SetupFaceUseCase,
    resolveCurrentUserId: suspend () -> Int?,
    setFaceSetupState: (FaceSetupState) -> Unit,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    onServicePersonVerified: () -> Unit,
    textResolver: ResourceTextResolver,
): Unit = when (event) {
        FaceSdkEvent.InitSuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf(
                    "verificationType" to VerificationType.SERVICE_PERSON,
                    "flow" to "face_setup",
                ),
            )
            showToast(textResolver.text(R.string.identification_face_init_success))
            setFaceSetupState(FaceSetupState.Initial)
        }
        is FaceSdkEvent.InitFailed -> setFaceSetupError(
            buildFaceVerifyErrorMessage(
                textResolver,
                R.string.identification_face_verification_init_failed,
                event.error,
            )
        )
        is FaceSdkEvent.VerifySuccess -> {
            showToast(
                textResolver.text(R.string.identification_face_setup_verification_succeeded),
            )
            launchFaceSetupUpload(
                scope = scope,
                setupFaceUseCase = setupFaceUseCase,
                resolveCurrentUserId = resolveCurrentUserId,
                imageFile = ready.imageFile,
                base64Image = ready.base64Image,
                onUploading = { setFaceSetupState(FaceSetupState.UploadingImage) },
                onSuccess = {
                    setFaceSetupState(FaceSetupState.Success)
                    FaceVerificationEventTracker.trackEvent(
                        eventType = EventType.FACE_SETUP_UPLOAD_SUCCESS,
                        extras = mapOf("flow" to "face_setup"),
                    )
                    showToast(textResolver.text(R.string.identification_face_setup_succeeded))
                    onServicePersonVerified()
                },
                onError = setFaceSetupError,
                textResolver = textResolver,
            )
        }
        is FaceSdkEvent.VerifyFailed -> setFaceSetupError(
            buildFaceVerifyErrorMessage(
                textResolver,
                R.string.identification_face_verification_failed,
                event.error,
            )
        )
        FaceSdkEvent.Cancelled -> setFaceSetupError(
            textResolver.text(R.string.identification_face_setup_cancelled),
        )
    }
