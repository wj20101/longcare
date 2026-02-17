package com.ytone.longcare.domain.faceauth

import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig

interface FaceVerificationConfigProvider {
    suspend fun getFaceVerificationConfig(): FaceVerificationConfig?
}

