package com.ytone.longcare.features.face.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.theme.PrimaryBlue

@Composable
internal fun CameraPreviewContent(
    onImageCapture: (ImageCapture?) -> Unit,
    onTakePhoto: () -> Unit,
    isCapturing: Boolean,
    onCameraError: (Exception) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember(context) { PreviewView(context) }
    val currentOnImageCapture by rememberUpdatedState(onImageCapture)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    var captureReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var capture: ImageCapture? = null
        captureReady = false
        currentOnImageCapture(null)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Camera provider initialization can complete after leaving this screen.
            if (!disposed) {
                try {
                    val activeProvider = future.get()
                    provider = activeProvider
                    val boundPreview = Preview.Builder().build()
                    val boundCapture = ImageCapture.Builder()
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    preview = boundPreview
                    capture = boundCapture
                    boundPreview.surfaceProvider = previewView.surfaceProvider
                    activeProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA,
                        boundPreview, boundCapture,
                    )
                    // Publish only after binding succeeds, never the unbound candidate.
                    currentOnImageCapture(boundCapture)
                    captureReady = true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    captureReady = false
                    currentOnImageCapture(null)
                    currentOnCameraError(error)
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            captureReady = false
            currentOnImageCapture(null)
            // Release only this screen's use cases, not another camera owner's bindings.
            val useCases = listOfNotNull(preview, capture).toTypedArray()
            if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = { if (captureReady && !isCapturing) onTakePhoto() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp)
                .semantics { if (!captureReady || isCapturing) disabled() },
            containerColor = PrimaryBlue,
            contentColor = Color.White
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.face_capture_title),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Text(
                text = stringResource(R.string.face_capture_camera_instruction),
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
