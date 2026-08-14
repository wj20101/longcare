package com.ytone.longcare.features.identification.api

import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.StateFlow

data class IdentificationActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToCamera: (WatermarkData) -> Unit,
    val onNavigateToManualFaceCapture: () -> Unit,
    val onNavigateToDefaultFaceVerification: (OrderKey) -> Unit,
    val onNavigateToSelectService: (OrderKey) -> Unit,
    val capturedImageUriFlow: StateFlow<String?>,
    val clearCapturedImageUri: () -> Unit,
    val faceImagePathFlow: StateFlow<String?>,
    val clearFaceImagePath: () -> Unit,
    val defaultFaceVerificationResultFlow: StateFlow<Boolean?>,
    val clearDefaultFaceVerificationResult: () -> Unit,
)
