package com.ytone.longcare.features.photoupload.api

data class CameraActions(
    /** True once the caller's navigation entry owns the photo URI. */
    val onImageCaptured: (String) -> Boolean
)
