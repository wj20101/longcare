package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest

internal fun createFaceVerificationRequest(
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoBase64: String? = null,
): FaceVerificationRequest {
    return FaceVerificationRequest(
        name = name,
        idNo = idNo,
        orderNo = orderNo,
        userId = userId,
        sourcePhotoStr = sourcePhotoBase64
    )
}

internal fun createElderOrderNo(orderId: Long, now: Long = System.currentTimeMillis()): String {
    return "elder_${orderId}_$now"
}

internal fun createFaceSetupOrderNo(now: Long = System.currentTimeMillis()): String {
    return "face_setup_$now"
}
