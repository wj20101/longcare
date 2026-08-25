package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.features.identification.domain.VerifyServicePersonDecision
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType

internal fun handleServicePersonVerificationDecision(
    decision: VerifyServicePersonDecision,
    onRegisteredFaceAvailable: () -> Unit,
    onRequireFaceSetup: () -> Unit,
    onSessionInvalidated: () -> Unit,
    onVerificationFailure: (String, Throwable?) -> Unit,
    textResolver: ResourceTextResolver,
) {
    when (decision) {
        VerifyServicePersonDecision.VerifyRegisteredFace -> onRegisteredFaceAvailable()
        VerifyServicePersonDecision.RequireFaceSetup -> {
            FaceVerificationEventTracker.trackEvent(EventType.SERVICE_FACE_SETUP_REQUIRED)
            onRequireFaceSetup()
        }
        VerifyServicePersonDecision.SessionInvalidated -> onSessionInvalidated()
        is VerifyServicePersonDecision.Error -> onVerificationFailure(
            textResolver.resolve(decision.failure),
            null,
        )
    }
}
