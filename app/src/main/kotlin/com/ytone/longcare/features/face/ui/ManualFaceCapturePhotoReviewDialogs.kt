package com.ytone.longcare.features.face.ui

import androidx.compose.runtime.Composable

@Composable
internal fun PhotoReviewFullScreenPreviewDialog(
    showFullScreenPreview: Boolean,
    fullScreenFace: DetectedFace?,
    onDismiss: () -> Unit
) {
    if (showFullScreenPreview && fullScreenFace != null) {
        FaceFullScreenPreviewDialog(
            face = fullScreenFace,
            onDismiss = onDismiss
        )
    }
}
