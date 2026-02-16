package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

internal suspend fun startFaceVerificationWithResolvedConfigOrNotify(
    context: Context,
    request: FaceVerificationRequest,
    callback: FaceVerifyCallback,
    systemConfigManager: SystemConfigManager,
    faceVerifier: FaceVerifier,
    onConfigMissing: () -> Unit,
) {
    val config = systemConfigManager.getFaceVerificationConfig()
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
    systemConfigManager: SystemConfigManager,
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
        systemConfigManager = systemConfigManager,
        faceVerifier = faceVerifier,
        onConfigMissing = { onSetFaceVerificationError("人脸配置不可用", null) }
    )
}
