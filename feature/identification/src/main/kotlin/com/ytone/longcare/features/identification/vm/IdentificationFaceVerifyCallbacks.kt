package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType

internal fun buildFaceVerifyErrorMessage(prefix: String, error: FaceVerifyError?): String {
    val reason =
        error?.description
            ?.takeIf { it.isNotBlank() }
            ?: error?.reason?.takeIf { it.isNotBlank() }
            ?: "请稍后重试"
    return "$prefix：$reason"
}

internal fun handleIdentificationFlowFaceSdkEvent(
    event: FaceSdkEvent,
    currentVerificationType: () -> VerificationType?,
    setVerificationState: (FaceVerificationState) -> Unit,
    onSetFaceVerificationError: (String, FaceVerifyError?) -> Unit,
    onServicePersonVerified: () -> Unit,
    onElderVerified: () -> Unit,
    showToast: (String) -> Unit,
): Unit = when (event) {
        FaceSdkEvent.InitSuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_INIT_SUCCESS,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast("人脸验证初始化成功")
            setVerificationState(FaceVerificationState.Verifying)
        }
        is FaceSdkEvent.InitFailed -> onSetFaceVerificationError(
            buildFaceVerifyErrorMessage("人脸识别初始化失败", event.error),
            event.error,
        )
        is FaceSdkEvent.VerifySuccess -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_SUCCESS,
                extras = mapOf(
                    "verificationType" to currentVerificationType(),
                    "isSuccess" to event.result.isSuccess,
                ),
            )
            showToast("人脸验证成功")
            setVerificationState(FaceVerificationState.Success(event.result))

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
        }
        is FaceSdkEvent.VerifyFailed -> onSetFaceVerificationError(
            buildFaceVerifyErrorMessage("人脸验证失败", event.error),
            event.error,
        )
        FaceSdkEvent.Cancelled -> {
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.FACE_VERIFY_CANCELLED,
                extras = mapOf("verificationType" to currentVerificationType()),
            )
            showToast("人脸验证已取消")
            setVerificationState(FaceVerificationState.Cancelled)
        }
    }
