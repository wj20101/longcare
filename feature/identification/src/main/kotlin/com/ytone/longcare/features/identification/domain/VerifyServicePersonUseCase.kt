package com.ytone.longcare.features.identification.domain

import javax.inject.Inject

sealed interface VerifyServicePersonDecision {
    data class DownloadRemoteFace(
        val user: ServicePersonProfile,
        val sourcePhotoUrl: String,
    ) : VerifyServicePersonDecision

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
    suspend fun execute(user: ServicePersonProfile?): VerifyServicePersonDecision {
        if (user == null) {
            return VerifyServicePersonDecision.Error(
                ServicePersonVerificationFailure.CurrentUserUnavailable,
            )
        }

        return when (val source = dataGateway.resolveFaceSource()) {
            is ServicePersonFaceSource.RemoteFace -> VerifyServicePersonDecision.DownloadRemoteFace(
                user = user,
                sourcePhotoUrl = source.sourcePhotoUrl,
            )

            ServicePersonFaceSource.RequireFaceSetup -> {
                dataGateway.clearLegacyFaceArtifacts(user.userId)
                VerifyServicePersonDecision.RequireFaceSetup
            }

            ServicePersonFaceSource.SessionInvalidated ->
                VerifyServicePersonDecision.SessionInvalidated
        }
    }
}
