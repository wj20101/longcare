package com.ytone.longcare.features.photoupload.viewmodel

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.feature.photoupload.R

internal data class PhotoUploadMessages(
    val beforeService: String,
    val duringService: String,
    val afterService: String,
    val unknownCaregiver: String,
    val unknownElder: String,
    val cloudUploadFailed: String,
    val imageProcessingFailed: String,
)

internal fun ResourceTextResolver.photoUploadMessages(): PhotoUploadMessages = PhotoUploadMessages(
    beforeService = text(R.string.photo_watermark_before_service),
    duringService = text(R.string.photo_watermark_during_service),
    afterService = text(R.string.photo_watermark_after_service),
    unknownCaregiver = text(R.string.photo_watermark_unknown_caregiver),
    unknownElder = text(R.string.photo_watermark_unknown_elder),
    cloudUploadFailed = text(R.string.photo_cloud_upload_failed),
    imageProcessingFailed = text(R.string.photo_image_processing_failed),
)
