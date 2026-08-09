package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
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
) {
    scope.launch {
        try {
            setFaceSetupState(FaceSetupState.Initial)
            setFaceVerificationState(FaceVerificationState.Idle)
            showToast("开始处理人脸图片...")

            val preparation = prepareFaceSetupVerificationInput(
                imagePath = imagePath,
                faceDataSource = faceDataSource,
                currentUser = resolveCurrentUser()
            )
            if (preparation is FaceSetupPreparation.Error) {
                setFaceSetupError(preparation.message)
                return@launch
            }
            val ready = preparation as FaceSetupPreparation.Ready

            showToast("开始人脸验证和设置...")
            prepareSdkLaunch(ready.request, ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setFaceSetupError("处理人脸图片时发生错误: ${e.message}")
        }
    }
}
