package com.ytone.longcare.features.facecapture

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun FaceCaptureCameraPreview(
    cameraController: LifecycleCameraController,
    onStreamStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnStreamStateChanged by rememberUpdatedState(onStreamStateChanged)
    val previewView = remember(context) { PreviewView(context) }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { streamState ->
            latestOnStreamStateChanged(streamState == PreviewView.StreamState.STREAMING)
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)

        onDispose {
            previewView.previewStreamState.removeObserver(observer)
        }
    }

    AndroidView(
        factory = {
            previewView.apply { controller = cameraController }
        },
        update = { view -> view.controller = cameraController },
        modifier = modifier.semantics {
            contentDescription = "相机预览，用于人脸采集"
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FaceCaptureTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        modifier = modifier
    )
}
