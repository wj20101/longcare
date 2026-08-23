package com.ytone.longcare.features.identification.domain

sealed interface CheckFaceRemoteResult {
    data object Success : CheckFaceRemoteResult

    data class Rejected(val message: String?) : CheckFaceRemoteResult

    data object NetworkError : CheckFaceRemoteResult
}

interface CheckFaceGateway {
    suspend fun checkFace(
        orderId: Int,
        faceImageBase64: String,
    ): CheckFaceRemoteResult
}
