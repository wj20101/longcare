package com.ytone.longcare.features.identification.domain

sealed interface ServicePersonFaceSource {
    data class RemoteFace(val sourcePhotoUrl: String) : ServicePersonFaceSource

    data object RequireFaceSetup : ServicePersonFaceSource

    /** 会话失效已经由全局网络层处理，业务页面不得再发起本地导航。 */
    data object SessionInvalidated : ServicePersonFaceSource
}

interface VerifyServicePersonDataGateway {
    suspend fun resolveFaceSource(): ServicePersonFaceSource

    suspend fun clearLegacyFaceArtifacts(userId: Int)
}
