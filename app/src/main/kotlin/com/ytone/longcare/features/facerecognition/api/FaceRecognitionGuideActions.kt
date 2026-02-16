package com.ytone.longcare.features.facerecognition.api

import com.ytone.longcare.navigation.OrderNavParams

data class FaceRecognitionGuideActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToSelectService: (OrderNavParams) -> Unit
)
