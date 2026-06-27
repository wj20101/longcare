package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.domain.SetupFaceResult
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun createFaceSetupVerificationCallback(
    onInitSuccess: () -> Unit,
    onInitFailed: (FaceVerifyError?) -> Unit,
    onVerifySuccess: (FaceVerifyResult) -> Unit,
    onVerifyFailed: (FaceVerifyError?) -> Unit,
    onVerifyCancel: () -> Unit,
): FaceVerifyCallback {
    return createFaceVerifyCallback(
        onInitSuccess = onInitSuccess,
        onInitFailed = onInitFailed,
        onVerifySuccess = onVerifySuccess,
        onVerifyFailed = onVerifyFailed,
        onVerifyCancel = onVerifyCancel
    )
}

internal fun launchFaceSetupUpload(
    scope: CoroutineScope,
    setupFaceUseCase: SetupFaceUseCase,
    resolveCurrentUserId: suspend () -> Int?,
    imageFile: File,
    base64Image: String,
    onUploading: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
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
                is SetupFaceResult.Error -> onError(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError("上传失败: ${e.message ?: "请稍后重试"}")
        }
    }
}

internal fun createFaceSetupFlowVerifyCallback(
    imageFile: File,
    base64Image: String,
    scope: CoroutineScope,
    setupFaceUseCase: SetupFaceUseCase,
    resolveCurrentUserId: suspend () -> Int?,
    onInitSuccess: () -> Unit,
    onInitFailed: (FaceVerifyError?) -> Unit,
    onBeforeUpload: () -> Unit,
    onUploading: () -> Unit,
    onUploadSuccess: () -> Unit,
    onUploadError: (String) -> Unit,
    onVerifyFailed: (FaceVerifyError?) -> Unit,
    onVerifyCancel: () -> Unit,
): FaceVerifyCallback {
    return createFaceSetupVerificationCallback(
        onInitSuccess = onInitSuccess,
        onInitFailed = onInitFailed,
        onVerifySuccess = {
            onBeforeUpload()
            launchFaceSetupUpload(
                scope = scope,
                setupFaceUseCase = setupFaceUseCase,
                resolveCurrentUserId = resolveCurrentUserId,
                imageFile = imageFile,
                base64Image = base64Image,
                onUploading = onUploading,
                onSuccess = onUploadSuccess,
                onError = onUploadError
            )
        },
        onVerifyFailed = onVerifyFailed,
        onVerifyCancel = onVerifyCancel
    )
}

internal fun createStandardFaceSetupFlowVerifyCallback(
    ready: FaceSetupPreparation.Ready,
    scope: CoroutineScope,
    setupFaceUseCase: SetupFaceUseCase,
    resolveCurrentUserId: suspend () -> Int?,
    setFaceSetupState: (FaceSetupState) -> Unit,
    setFaceSetupError: (String) -> Unit,
    showToast: (String) -> Unit,
    onServicePersonVerified: () -> Unit,
): FaceVerifyCallback {
    return createFaceSetupFlowVerifyCallback(
        imageFile = ready.imageFile,
        base64Image = ready.base64Image,
        scope = scope,
        setupFaceUseCase = setupFaceUseCase,
        resolveCurrentUserId = resolveCurrentUserId,
        onInitSuccess = {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf(
                    "verificationType" to VerificationType.SERVICE_PERSON,
                    "flow" to "face_setup",
                ),
            )
            showToast("人脸验证初始化成功")
            setFaceSetupState(FaceSetupState.Initial)
        },
        onInitFailed = { error ->
            setFaceSetupError(buildFaceVerifyErrorMessage("人脸验证初始化失败", error))
        },
        onBeforeUpload = {
            showToast("人脸验证成功，开始上传设置...")
        },
        onUploading = { setFaceSetupState(FaceSetupState.UploadingImage) },
        onUploadSuccess = {
            setFaceSetupState(FaceSetupState.Success)
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_SETUP_UPLOAD_SUCCESS,
                extras = mapOf("flow" to "face_setup"),
            )
            showToast("人脸信息设置成功")
            onServicePersonVerified()
        },
        onUploadError = setFaceSetupError,
        onVerifyFailed = { error ->
            setFaceSetupError(buildFaceVerifyErrorMessage("人脸验证失败", error))
        },
        onVerifyCancel = {
            setFaceSetupError("用户取消了人脸验证")
        }
    )
}
