package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
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
