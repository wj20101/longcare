package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.features.identification.domain.VerifyServicePersonDecision

internal fun handleServicePersonVerificationDecision(
    decision: VerifyServicePersonDecision,
    onUseCachedFace: (VerifyServicePersonDecision.UseCachedFace) -> Unit,
    onDownloadAndCache: (VerifyServicePersonDecision.DownloadAndCache) -> Unit,
    onRequireFaceSetup: () -> Unit,
    onError: (String) -> Unit,
) {
    when (decision) {
        is VerifyServicePersonDecision.UseCachedFace -> onUseCachedFace(decision)
        is VerifyServicePersonDecision.DownloadAndCache -> onDownloadAndCache(decision)
        VerifyServicePersonDecision.RequireFaceSetup -> onRequireFaceSetup()
        is VerifyServicePersonDecision.Error -> onError(decision.message)
    }
}
