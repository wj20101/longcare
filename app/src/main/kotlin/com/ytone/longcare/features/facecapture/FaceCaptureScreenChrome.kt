package com.ytone.longcare.features.facecapture

import androidx.camera.view.LifecycleCameraController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun FaceCaptureCameraPreview(
    cameraController: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            androidx.camera.view.PreviewView(context).apply {
                controller = cameraController
            }
        },
        modifier = modifier.semantics {
            contentDescription = "相机预览，用于人脸捕获"
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FaceCaptureTopBar(
    hasCapturedFaces: Boolean,
    onNavigateBack: () -> Unit,
    onClearAllFaces: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "人脸捕获",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        },
        actions = {
            if (hasCapturedFaces) {
                IconButton(onClick = onClearAllFaces) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "清空所有照片",
                        tint = Color.White
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        modifier = modifier
    )
}
