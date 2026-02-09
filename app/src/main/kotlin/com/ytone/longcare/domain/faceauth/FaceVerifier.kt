package com.ytone.longcare.domain.faceauth

import android.content.Context
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult

interface FaceVerifyCallback {
    fun onInitSuccess()
    fun onInitFailed(error: FaceVerifyError?)
    fun onVerifySuccess(result: FaceVerifyResult)
    fun onVerifyFailed(error: FaceVerifyError?)
    fun onVerifyCancel()
}

interface FaceVerifier {
    suspend fun startFaceVerification(
        context: Context,
        config: FaceVerificationConfig,
        request: FaceVerificationRequest,
        callback: FaceVerifyCallback
    )

    fun release()
}
