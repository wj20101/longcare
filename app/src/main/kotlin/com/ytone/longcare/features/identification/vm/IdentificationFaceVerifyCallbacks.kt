package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult

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
    return "$prefix: ${error?.description ?: "未知错误"} (错误码: ${error?.code ?: "无"})"
}
