package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.ytone.longcare.common.image.ImageProcessingPolicies
import com.ytone.longcare.common.image.UnifiedJpegEncoder
import java.io.File

internal data class ProcessedFacePhoto(
    val bitmap: Bitmap,
    val base64: String
)

internal fun loadProcessedFacePhoto(imagePath: String): ProcessedFacePhoto {
    val imageFile = File(imagePath)
    if (!imageFile.exists()) {
        throw IllegalStateException("图片文件不存在")
    }

    val bitmap = BitmapFactory.decodeFile(imagePath)
        ?: throw IllegalStateException("图片处理失败")

    val imageBytes =
        UnifiedJpegEncoder.encode(
            bitmap = bitmap,
            policy = ImageProcessingPolicies.FACE_PHOTO,
        ).bytes
    val sourcePhotoBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

    return ProcessedFacePhoto(
        bitmap = bitmap,
        base64 = sourcePhotoBase64
    )
}
