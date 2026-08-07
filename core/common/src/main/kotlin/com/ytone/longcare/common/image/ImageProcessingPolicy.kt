package com.ytone.longcare.common.image

import com.ytone.longcare.common.constants.CosConstants

/**
 * 图片输出策略的唯一来源，避免各业务自行硬编码尺寸、质量和文件大小。
 */
data class ImageProcessingPolicy(
    val targetShortEdgePx: Int?,
    val initialJpegQuality: Int,
    val minimumJpegQuality: Int,
    val jpegQualityStep: Int,
    val maxOutputBytes: Long,
) {
    init {
        require(targetShortEdgePx == null || targetShortEdgePx > 0)
        require(initialJpegQuality in 1..100)
        require(minimumJpegQuality in 1..initialJpegQuality)
        require(jpegQualityStep > 0)
        require(maxOutputBytes > 0L)
    }
}

object ImageProcessingPolicies {
    val WATERMARKED_PHOTO =
        ImageProcessingPolicy(
            targetShortEdgePx = 1080,
            initialJpegQuality = 90,
            minimumJpegQuality = 70,
            jpegQualityStep = 5,
            maxOutputBytes = CosConstants.MAX_IMAGE_FILE_SIZE_BYTES,
        )

    val FACE_PHOTO =
        ImageProcessingPolicy(
            targetShortEdgePx = null,
            initialJpegQuality = 90,
            minimumJpegQuality = 75,
            jpegQualityStep = 5,
            maxOutputBytes = CosConstants.MAX_IMAGE_FILE_SIZE_BYTES,
        )
}

enum class ManagedImagePurpose(
    internal val directoryName: String,
    internal val useExternalPicturesDirectory: Boolean,
) {
    WATERMARKED_PHOTO(
        directoryName = "captured_photos",
        useExternalPicturesDirectory = true,
    ),
    MANUAL_FACE_CAPTURE(
        directoryName = "face_captures",
        useExternalPicturesDirectory = false,
    ),
}
