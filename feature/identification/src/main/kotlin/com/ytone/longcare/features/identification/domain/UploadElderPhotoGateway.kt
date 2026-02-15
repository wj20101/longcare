package com.ytone.longcare.features.identification.domain

import android.net.Uri

sealed interface UploadElderPhotoSourceResult {
    data class Success(val uploadedKey: String) : UploadElderPhotoSourceResult

    data class Error(val message: String) : UploadElderPhotoSourceResult
}

sealed interface UploadElderPhotoOrderResult {
    data object Success : UploadElderPhotoOrderResult

    data class Error(val message: String) : UploadElderPhotoOrderResult
}

interface UploadElderPhotoGateway {
    suspend fun uploadPhoto(photoUri: Uri): UploadElderPhotoSourceResult

    suspend fun uploadOrderStartImage(orderId: Long, uploadedKey: String): UploadElderPhotoOrderResult
}
