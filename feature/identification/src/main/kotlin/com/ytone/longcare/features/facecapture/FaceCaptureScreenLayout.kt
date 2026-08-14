package com.ytone.longcare.features.facecapture

import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun FaceCaptureCameraLayout(
    uiState: FaceCaptureUiState,
    cameraController: LifecycleCameraController,
    title: String,
    onNavigateBack: () -> Unit,
    onPreviewStreamStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FaceCaptureCameraPreview(
            cameraController = cameraController,
            onStreamStateChanged = onPreviewStreamStateChanged,
            modifier = Modifier.fillMaxSize()
        )

        FaceCaptureTopBar(
            title = title,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        FaceDetectionGuide(
            uiState = uiState,
            modifier = Modifier.align(Alignment.Center)
        )

        if (uiState.phase == FaceCapturePhase.PREPARING) {
            FacePreparationCountdown(
                seconds = uiState.countdownSeconds,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        FaceCaptureBottomPanel(
            uiState = uiState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
