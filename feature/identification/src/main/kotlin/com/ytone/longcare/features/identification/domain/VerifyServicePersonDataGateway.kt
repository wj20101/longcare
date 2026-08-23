package com.ytone.longcare.features.identification.domain

sealed interface ServicePersonFaceSource {
    data class RemoteFace(val sourcePhotoUrl: String) : ServicePersonFaceSource

    data object RequireFaceSetup : ServicePersonFaceSource

    data class Rejected(val message: String?) : ServicePersonFaceSource

    data object NetworkError : ServicePersonFaceSource
}

interface VerifyServicePersonDataGateway {
    suspend fun readCachedFace(userId: Int): String?

    suspend fun resolveFaceSource(): ServicePersonFaceSource
}
