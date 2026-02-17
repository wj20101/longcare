package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchStandardFaceVerification(
    scope: CoroutineScope,
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    verificationType: VerificationType,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (Context, FaceVerificationRequest) -> Unit,
) {
    scope.launch {
        beginVerification(verificationType)
        val request = createFaceVerificationRequest(
            name = name,
            idNo = idNo,
            orderNo = orderNo,
            userId = userId
        )
        startVerificationWithRequest(context, request)
    }
}
