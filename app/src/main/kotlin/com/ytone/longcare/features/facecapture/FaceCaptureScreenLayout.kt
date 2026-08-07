package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ytone.longcare.core.ui.image.PhotoPreviewDialog

@Composable
internal fun FaceCaptureCameraLayout(
    uiState: FaceCaptureUiState,
    cameraController: LifecycleCameraController,
    onNavigateBack: () -> Unit,
    onClearAllFaces: () -> Unit,
    onSelectFace: (Int) -> Unit,
    onRemoveFace: (Int) -> Unit,
    onConfirmSelectedFace: () -> Unit,
    onCancelSelection: () -> Unit,
    previewBitmap: Bitmap?,
    onPreviewBitmapChange: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FaceCaptureCameraPreview(
            cameraController = cameraController,
            modifier = Modifier.fillMaxSize()
        )

        FaceCaptureTopBar(
            hasCapturedFaces = uiState.hasCapturedFaces,
            onNavigateBack = onNavigateBack,
            onClearAllFaces = onClearAllFaces,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        FaceDetectionIndicator(
            detected = uiState.faceDetected,
            quality = uiState.faceQuality,
            modifier = Modifier.align(Alignment.Center)
        )

        FaceCaptureBottomPanel(
            uiState = uiState,
            onThumbnailClick = { index, bitmap, isSelected ->
                if (isSelected) {
                    onPreviewBitmapChange(bitmap)
                } else {
                    onSelectFace(index)
                }
            },
            onDeleteFace = onRemoveFace,
            onConfirmSelectedFace = onConfirmSelectedFace,
            onCancelSelection = onCancelSelection,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        uiState.error?.let { error ->
            LaunchedEffect(error) {
                // 预留错误提示（如 Snackbar）接入点
            }
        }

        previewBitmap?.let { bitmap ->
            PhotoPreviewDialog(
                imageModel = bitmap,
                onDismiss = { onPreviewBitmapChange(null) }
            )
        }
    }
}
