package com.ytone.longcare.navigation

import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData

internal fun createIdentificationRouteActions(
    savedStateHandle: SavedStateHandle,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: (WatermarkData) -> Unit,
    onNavigateToManualFaceCapture: () -> Unit,
    onNavigateToDefaultFaceVerification: (OrderKey) -> Unit,
    onNavigateToSelectService: (OrderKey) -> Unit,
): IdentificationActions = IdentificationActions(
    onNavigateBack = onNavigateBack,
    onNavigateToCamera = onNavigateToCamera,
    onNavigateToManualFaceCapture = onNavigateToManualFaceCapture,
    onNavigateToDefaultFaceVerification = onNavigateToDefaultFaceVerification,
    onNavigateToSelectService = onNavigateToSelectService,
    capturedImageUriFlow = savedStateHandle.getStateFlow(
        NavigationConstants.CAPTURED_IMAGE_URI_KEY,
        null,
    ),
    clearCapturedImageUri = {
        savedStateHandle[NavigationConstants.CAPTURED_IMAGE_URI_KEY] = null
    },
    faceImagePathFlow = savedStateHandle.getStateFlow(
        NavigationConstants.FACE_IMAGE_PATH_KEY,
        null,
    ),
    clearFaceImagePath = {
        savedStateHandle[NavigationConstants.FACE_IMAGE_PATH_KEY] = null
    },
    defaultFaceVerificationResultFlow = savedStateHandle.getStateFlow(
        NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
        null,
    ),
    clearDefaultFaceVerificationResult = {
        savedStateHandle[NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY] = null
    },
)
