package com.ytone.longcare.features.identification.domain

sealed interface ServicePersonFaceSource {
    data class RemoteFace(val sourcePhotoUrl: String) : ServicePersonFaceSource

    data object RequireFaceSetup : ServicePersonFaceSource

    data class Error(val message: String) : ServicePersonFaceSource
}

interface VerifyServicePersonDataGateway {
    suspend fun readCachedFace(userId: Int): String?

    suspend fun resolveFaceSource(): ServicePersonFaceSource
}
