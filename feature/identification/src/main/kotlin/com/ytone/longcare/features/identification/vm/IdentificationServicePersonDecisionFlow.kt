package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDecision
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import kotlinx.coroutines.CoroutineScope

internal fun handleServicePersonVerificationDecision(
    decision: VerifyServicePersonDecision,
    scope: CoroutineScope,
    createOrderNo: () -> String,
    faceDataSource: IdentificationFaceDataSource,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (FaceVerificationRequest) -> Unit,
    onRequireFaceSetup: () -> Unit,
    onVerificationFailure: (String, Throwable?) -> Unit,
    textResolver: ResourceTextResolver,
) {
    when (decision) {
        is VerifyServicePersonDecision.DownloadRemoteFace -> {
            val orderNo = createOrderNo()
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.SERVICE_REMOTE_FACE_SELECTED,
                extras = FaceVerificationEventTracker.safeUrlExtras(decision.sourcePhotoUrl) + mapOf(
                    "userId" to decision.user.userId,
                    "orderNo" to orderNo,
                ),
            )
            launchSelfProvidedFaceVerificationWithRemoteSource(
                scope = scope,
                name = decision.user.userName,
                idNo = decision.user.identityCardNumber,
                orderNo = orderNo,
                userId = decision.user.userId.toString(),
                sourceUserId = decision.user.userId,
                sourcePhotoUrl = decision.sourcePhotoUrl,
                faceDataSource = faceDataSource,
                beginVerification = beginVerification,
                startVerificationWithRequest = startVerificationWithRequest,
                onFailure = { message -> onVerificationFailure(message, null) },
                textResolver = textResolver,
            )
        }
        VerifyServicePersonDecision.RequireFaceSetup -> {
            FaceVerificationEventTracker.trackEvent(EventType.SERVICE_FACE_SETUP_REQUIRED)
            onRequireFaceSetup()
        }
        VerifyServicePersonDecision.SessionInvalidated -> Unit
        is VerifyServicePersonDecision.Error -> onVerificationFailure(
            textResolver.resolve(decision.failure),
            null,
        )
    }
}
