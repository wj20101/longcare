package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal fun launchFaceCaptureResultHandling(
    scope: CoroutineScope,
    context: Context,
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    resolveCurrentUser: suspend () -> User?,
    setupFaceUseCase: SetupFaceUseCase,
    configProvider: FaceVerificationConfigProvider,
    faceVerifier: FaceVerifier,
    setFaceSetupState: (FaceSetupState) -> Unit,
    setFaceVerificationState: (FaceVerificationState) -> Unit,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    onServicePersonVerified: () -> Unit,
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
            startFaceVerificationWithResolvedConfigOrNotify(
                context = context,
                request = ready.request,
                callback = createStandardFaceSetupFlowVerifyCallback(
                    ready = ready,
                    scope = scope,
                    setupFaceUseCase = setupFaceUseCase,
                    resolveCurrentUserId = { resolveCurrentUser()?.userId },
                    setFaceSetupState = setFaceSetupState,
                    setFaceSetupError = setFaceSetupError,
                    showToast = showToast,
                    onServicePersonVerified = onServicePersonVerified,
                ),
                configProvider = configProvider,
                faceVerifier = faceVerifier,
                onConfigMissing = { setFaceSetupError("人脸配置不可用") }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setFaceSetupError("处理人脸图片时发生错误: ${e.message}")
        }
    }
}

internal fun launchFaceCaptureResultHandlingWithBindings(
    scope: CoroutineScope,
    context: Context,
    imagePath: String,
    faceDataSource: IdentificationFaceDataSource,
    resolveCurrentUser: suspend () -> User?,
    setupFaceUseCase: SetupFaceUseCase,
    configProvider: FaceVerificationConfigProvider,
    faceVerifier: FaceVerifier,
    faceSetupState: MutableStateFlow<FaceSetupState>,
    faceVerificationState: MutableStateFlow<FaceVerificationState>,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    onServicePersonVerified: () -> Unit,
) {
    launchFaceCaptureResultHandling(
        scope = scope,
        context = context,
        imagePath = imagePath,
        faceDataSource = faceDataSource,
        resolveCurrentUser = resolveCurrentUser,
        setupFaceUseCase = setupFaceUseCase,
        configProvider = configProvider,
        faceVerifier = faceVerifier,
        setFaceSetupState = { state -> faceSetupState.value = state },
        setFaceVerificationState = { state -> faceVerificationState.value = state },
        setFaceSetupError = setFaceSetupError,
        showToast = showToast,
        onServicePersonVerified = onServicePersonVerified,
    )
}
