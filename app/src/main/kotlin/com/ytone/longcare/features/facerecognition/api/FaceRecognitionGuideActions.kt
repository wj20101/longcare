package com.ytone.longcare.features.facerecognition.api

import com.ytone.longcare.model.OrderKey

data class FaceRecognitionGuideActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToSelectService: (OrderKey) -> Unit
)
