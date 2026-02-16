package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchFaceCaptureResultHandling(
    scope: CoroutineScope,
    context: Context,
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    resolveCurrentUser: suspend () -> User?,
    setupFaceUseCase: SetupFaceUseCase,
    setFaceSetupState: (FaceSetupState) -> Unit,
    setFaceVerificationState: (FaceVerificationState) -> Unit,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    onServicePersonVerified: () -> Unit,
    startFaceVerificationWithResolvedConfig: suspend (
        Context,
        FaceVerificationRequest,
        FaceVerifyCallback,
        () -> Unit,
    ) -> Unit,
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
            startFaceVerificationWithResolvedConfig(
                context,
                ready.request,
                createStandardFaceSetupFlowVerifyCallback(
                    ready = ready,
                    scope = scope,
                    setupFaceUseCase = setupFaceUseCase,
                    resolveCurrentUserId = { resolveCurrentUser()?.userId },
                    setFaceSetupState = setFaceSetupState,
                    setFaceSetupError = setFaceSetupError,
                    showToast = showToast,
                    onServicePersonVerified = onServicePersonVerified,
                ),
                { setFaceSetupError("人脸配置不可用") }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setFaceSetupError("处理人脸图片时发生错误: ${e.message}")
        }
    }
}
