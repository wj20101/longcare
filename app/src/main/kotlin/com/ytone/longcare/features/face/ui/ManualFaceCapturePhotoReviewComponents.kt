package com.ytone.longcare.features.face.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
internal fun PhotoReviewContent(
    bitmap: Bitmap?,
    detectedFaces: List<DetectedFace>,
    selectedFaceIndex: Int?,
    isProcessingFaces: Boolean,
    onFaceSelected: (Int) -> Unit,
    onRetakePhoto: () -> Unit,
    currentState: ManualFaceCaptureState
) {
    var showFullScreenPreview by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf<DetectedFace?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PhotoReviewPreviewPane(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            bitmap = bitmap,
            detectedFaces = detectedFaces,
            selectedFaceIndex = selectedFaceIndex,
            isProcessingFaces = isProcessingFaces
        )

        PhotoReviewStatePanel(
            currentState = currentState,
            detectedFaces = detectedFaces,
            selectedFaceIndex = selectedFaceIndex,
            onFaceSelected = onFaceSelected,
            onFaceLongClick = { face ->
                fullScreenFace = face
                showFullScreenPreview = true
            },
            onRetakePhoto = onRetakePhoto
        )
    }

    PhotoReviewFullScreenPreviewDialog(
        showFullScreenPreview = showFullScreenPreview,
        fullScreenFace = fullScreenFace,
        onDismiss = {
            showFullScreenPreview = false
            fullScreenFace = null
        }
    )
}
