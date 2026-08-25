package com.ytone.longcare.features.identification.domain

import javax.inject.Inject

sealed interface VerifyServicePersonDecision {
    data object VerifyRegisteredFace : VerifyServicePersonDecision

    data object RequireFaceSetup : VerifyServicePersonDecision

    /** 全局会话失效流程已经接管，当前业务页面不再执行导航。 */
    data object SessionInvalidated : VerifyServicePersonDecision

    data class Error(val failure: ServicePersonVerificationFailure) : VerifyServicePersonDecision
}

sealed interface ServicePersonVerificationFailure {
    data object CurrentUserUnavailable : ServicePersonVerificationFailure
}

class VerifyServicePersonUseCase @Inject constructor(
    private val dataGateway: VerifyServicePersonDataGateway,
) {
    suspend fun execute(userId: Int?): VerifyServicePersonDecision {
        if (userId == null) {
            return VerifyServicePersonDecision.Error(
                ServicePersonVerificationFailure.CurrentUserUnavailable,
            )
        }

        return when (val source = dataGateway.resolveFaceSource()) {
            ServicePersonFaceSource.RegisteredFaceAvailable ->
                VerifyServicePersonDecision.VerifyRegisteredFace

            ServicePersonFaceSource.RequireFaceSetup -> {
                dataGateway.clearLocalFaceArtifacts(userId)
                VerifyServicePersonDecision.RequireFaceSetup
            }

            ServicePersonFaceSource.SessionInvalidated ->
                VerifyServicePersonDecision.SessionInvalidated
        }
    }
}
