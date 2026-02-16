package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.domain.SetupFaceResult
import com.ytone.longcare.features.identification.domain.SetupFaceUseCase
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
            onError("上传失败: ${e.message}")
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
