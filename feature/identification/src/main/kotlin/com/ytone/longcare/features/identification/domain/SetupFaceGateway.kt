package com.ytone.longcare.features.identification.domain

import java.io.File

sealed interface SetupFaceUploadResult {
    data class Success(val uploadedKey: String) : SetupFaceUploadResult

    data class Error(val detail: String?) : SetupFaceUploadResult
}

sealed interface SetupFaceServerResult {
    data object Success : SetupFaceServerResult

    data class Rejected(val message: String?) : SetupFaceServerResult

    data object NetworkError : SetupFaceServerResult
}

interface SetupFaceGateway {
    suspend fun uploadFaceImage(imageFile: File): SetupFaceUploadResult

    suspend fun setFaceOnServer(base64Image: String, uploadedKey: String): SetupFaceServerResult

    suspend fun cacheUserFace(userId: Int, base64Image: String): Boolean

    suspend fun refreshCurrentUserSession()
}
