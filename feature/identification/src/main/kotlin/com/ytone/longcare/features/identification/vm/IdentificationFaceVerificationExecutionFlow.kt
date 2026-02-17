package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

internal suspend fun startFaceVerificationWithResolvedConfigOrNotify(
    context: Context,
    request: FaceVerificationRequest,
    callback: FaceVerifyCallback,
    configProvider: FaceVerificationConfigProvider,
    faceVerifier: FaceVerifier,
    onConfigMissing: () -> Unit,
) {
    val config = configProvider.getFaceVerificationConfig()
    if (config == null) {
        onConfigMissing()
        return
    }

    faceVerifier.startFaceVerification(
        context = context,
        config = config,
        request = request,
        callback = callback
    )
}

internal suspend fun startFaceVerificationWithIdentificationBindings(
    context: Context,
    request: FaceVerificationRequest,
    currentVerificationType: () -> VerificationType?,
    setVerificationState: (FaceVerificationState) -> Unit,
    onSetFaceVerificationError: (String, FaceVerifyError?) -> Unit,
    onServicePersonVerified: () -> Unit,
    onElderVerified: () -> Unit,
    showToast: (String) -> Unit,
    configProvider: FaceVerificationConfigProvider,
    faceVerifier: FaceVerifier,
) {
    startFaceVerificationWithResolvedConfigOrNotify(
        context = context,
        request = request,
        callback = createIdentificationFlowVerifyCallback(
            currentVerificationType = currentVerificationType,
            setVerificationState = setVerificationState,
            onSetFaceVerificationError = onSetFaceVerificationError,
            onServicePersonVerified = onServicePersonVerified,
            onElderVerified = onElderVerified,
            showToast = showToast,
        ),
        configProvider = configProvider,
        faceVerifier = faceVerifier,
        onConfigMissing = { onSetFaceVerificationError("人脸配置不可用", null) }
    )
}
