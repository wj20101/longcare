package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchFaceCaptureResultHandling(
    scope: CoroutineScope,
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    resolveCurrentUser: suspend () -> User?,
    setFaceSetupState: (FaceSetupState) -> Unit,
    setFaceVerificationState: (FaceVerificationState) -> Unit,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    prepareSdkLaunch: suspend (FaceVerificationRequest, FaceSetupPreparation.Ready) -> Unit,
    textResolver: ResourceTextResolver,
) {
    scope.launch {
        try {
            setFaceSetupState(FaceSetupState.Initial)
            setFaceVerificationState(FaceVerificationState.Idle)
            showToast(textResolver.text(R.string.identification_face_image_processing_started))

            val preparation = prepareFaceSetupVerificationInput(
                imagePath = imagePath,
                faceDataSource = faceDataSource,
                currentUser = resolveCurrentUser()
            )
            if (preparation is FaceSetupPreparation.Error) {
                setFaceSetupError(textResolver.resolve(preparation.failure))
                return@launch
            }
            val ready = preparation as FaceSetupPreparation.Ready

            showToast(textResolver.text(R.string.identification_face_setup_starting))
            prepareSdkLaunch(ready.request, ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setFaceSetupError(
                textResolver.text(R.string.identification_face_image_processing_error),
            )
        }
    }
}
