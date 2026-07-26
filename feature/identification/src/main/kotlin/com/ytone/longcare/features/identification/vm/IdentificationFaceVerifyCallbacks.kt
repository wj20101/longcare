package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType

internal fun createFaceVerifyCallback(
    onInitSuccess: () -> Unit,
    onInitFailed: (FaceVerifyError?) -> Unit,
    onVerifySuccess: (FaceVerifyResult) -> Unit,
    onVerifyFailed: (FaceVerifyError?) -> Unit,
    onVerifyCancel: () -> Unit,
): FaceVerifyCallback {
    return object : FaceVerifyCallback {
        override fun onInitSuccess() = onInitSuccess()

        override fun onInitFailed(error: FaceVerifyError?) = onInitFailed(error)

        override fun onVerifySuccess(result: FaceVerifyResult) = onVerifySuccess(result)

        override fun onVerifyFailed(error: FaceVerifyError?) = onVerifyFailed(error)

        override fun onVerifyCancel() = onVerifyCancel()
    }
}

internal fun buildFaceVerifyErrorMessage(prefix: String, error: FaceVerifyError?): String {
    val reason =
        error?.description
            ?.takeIf { it.isNotBlank() }
            ?: error?.reason?.takeIf { it.isNotBlank() }
            ?: "请稍后重试"
    return "$prefix：$reason"
}

internal fun createIdentificationFlowVerifyCallback(
    currentVerificationType: () -> VerificationType?,
    setVerificationState: (FaceVerificationState) -> Unit,
    onSetFaceVerificationError: (String, FaceVerifyError?) -> Unit,
    onServicePersonVerified: () -> Unit,
    onElderVerified: () -> Unit,
    showToast: (String) -> Unit,
): FaceVerifyCallback {
    return createFaceVerifyCallback(
        onInitSuccess = {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast("人脸验证初始化成功")
            setVerificationState(FaceVerificationState.Verifying)
        },
        onInitFailed = { error ->
            onSetFaceVerificationError(buildFaceVerifyErrorMessage("人脸识别初始化失败", error), error)
        },
        onVerifySuccess = { result ->
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_SUCCESS,
                extras = mapOf(
                    "verificationType" to currentVerificationType(),
                    "isSuccess" to result.isSuccess,
                ),
            )
            showToast("人脸验证成功")
            setVerificationState(FaceVerificationState.Success(result))

            when (currentVerificationType()) {
                VerificationType.SERVICE_PERSON -> {
                    onServicePersonVerified()
                    showToast("服务人员身份验证成功")
                }

                VerificationType.ELDER -> {
                    onElderVerified()
                    showToast("老人身份验证成功")
                }

                null -> {
                    showToast("验证类型未知，请重新操作")
                }
            }
        },
        onVerifyFailed = { error ->
            onSetFaceVerificationError(buildFaceVerifyErrorMessage("人脸验证失败", error), error)
        },
        onVerifyCancel = {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_CANCELLED,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast("人脸验证已取消")
            setVerificationState(FaceVerificationState.Cancelled)
        }
    )
}
