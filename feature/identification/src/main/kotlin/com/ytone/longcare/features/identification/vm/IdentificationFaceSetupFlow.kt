package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.faceauth.FaceSdkEvent
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
): Unit = when (event) {
        FaceSdkEvent.InitSuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf(
                    "verificationType" to VerificationType.SERVICE_PERSON,
                    "flow" to "face_setup",
                ),
            )
            showToast("人脸验证初始化成功")
            setFaceSetupState(FaceSetupState.Initial)
        }
        is FaceSdkEvent.InitFailed -> setFaceSetupError(
            buildFaceVerifyErrorMessage("人脸验证初始化失败", event.error)
        )
        is FaceSdkEvent.VerifySuccess -> {
            showToast("人脸验证成功，开始上传设置...")
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
                    showToast("人脸信息设置成功")
                    onServicePersonVerified()
                },
                onError = setFaceSetupError,
            )
        }
        is FaceSdkEvent.VerifyFailed -> setFaceSetupError(
            buildFaceVerifyErrorMessage("人脸验证失败", event.error)
        )
        FaceSdkEvent.Cancelled -> setFaceSetupError("用户取消了人脸验证")
    }
