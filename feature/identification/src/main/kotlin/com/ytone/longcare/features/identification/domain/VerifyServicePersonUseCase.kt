package com.ytone.longcare.features.identification.domain

import javax.inject.Inject

sealed interface VerifyServicePersonDecision {
    data class UseCachedFace(
        val user: ServicePersonProfile,
        val sourcePhotoBase64: String,
    ) : VerifyServicePersonDecision

    data class DownloadAndCache(
        val user: ServicePersonProfile,
        val sourcePhotoUrl: String,
    ) : VerifyServicePersonDecision

    data object RequireFaceSetup : VerifyServicePersonDecision

    data class Error(val message: String) : VerifyServicePersonDecision
}

class VerifyServicePersonUseCase @Inject constructor(
    private val dataGateway: VerifyServicePersonDataGateway,
) {
    suspend fun execute(user: ServicePersonProfile?): VerifyServicePersonDecision {
        if (user == null) {
            return VerifyServicePersonDecision.Error("无法获取用户信息")
        }

        val cachedBase64 = dataGateway.readCachedFace(user.userId)
        if (!cachedBase64.isNullOrBlank()) {
            return VerifyServicePersonDecision.UseCachedFace(
                user = user,
                sourcePhotoBase64 = cachedBase64,
            )
        }

        return when (val source = dataGateway.resolveFaceSource()) {
            is ServicePersonFaceSource.RemoteFace -> VerifyServicePersonDecision.DownloadAndCache(
                user = user,
                sourcePhotoUrl = source.sourcePhotoUrl,
            )

            ServicePersonFaceSource.RequireFaceSetup -> VerifyServicePersonDecision.RequireFaceSetup

            is ServicePersonFaceSource.Error -> VerifyServicePersonDecision.Error(source.message)
        }
    }
}
