package com.ytone.longcare.features.photoupload.api

data class CameraActions(
    val onImageCaptured: (String) -> Unit
)
