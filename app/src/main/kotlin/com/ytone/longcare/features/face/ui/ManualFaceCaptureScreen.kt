package com.ytone.longcare.features.face.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.face.viewmodel.ManualFaceCaptureViewModel
import com.ytone.longcare.theme.PrimaryBlue
import java.util.concurrent.Executors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ManualFaceCaptureScreen(
    onNavigateBack: () -> Unit,
    onFaceCaptured: (String) -> Unit,
    viewModel: ManualFaceCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by viewModel.currentState.collectAsStateWithLifecycle()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCameraPermissionGranted(isGranted)
    }

    ManualFaceCaptureEffects(
        context = context,
        currentState = currentState,
        savedFaceImagePath = uiState.savedFaceImagePath,
        onSetCameraPermissionGranted = viewModel::setCameraPermissionGranted,
        onFaceCaptured = onFaceCaptured,
        requestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.cameraPermissionGranted -> {
                    PermissionDeniedContent(
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }

                uiState.capturedPhoto == null -> {
                    CameraPreviewContent(
                        onImageCapture = { capture -> imageCapture = capture },
                        onTakePhoto = {
                            viewModel.startCapture()
                            takeManualFacePhoto(imageCapture, captureExecutor, viewModel)
                        },
                        isCapturing = currentState is ManualFaceCaptureState.CapturingPhoto
                    )
                }

                else -> {
                    PhotoReviewContent(
                        bitmap = uiState.capturedPhoto,
                        detectedFaces = uiState.detectedFaces,
                        selectedFaceIndex = uiState.selectedFaceIndex,
                        isProcessingFaces = uiState.isProcessingFaces,
                        onFaceSelected = viewModel::selectFace,
                        onRetakePhoto = viewModel::resetState,
                        currentState = currentState
                    )
                }
            }

            ManualFaceCaptureErrorOverlay(
                errorMessage = uiState.errorMessage,
                onClearError = viewModel::clearError,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            ManualFaceCaptureLoadingOverlay(
                isLoading = uiState.isLoading
            )
        }
    }

    if (uiState.showConfirmationDialog) {
        FaceConfirmationDialog(
            selectedFace = uiState.selectedFaceIndex?.let { index ->
                uiState.detectedFaces.getOrNull(index)
            },
            qualityHints = uiState.selectedFaceIndex?.let { index ->
                viewModel.getFaceQualityHints(index)
            } ?: emptyList(),
            onConfirm = viewModel::confirmSelectedFace,
            onCancel = viewModel::cancelAndRetake,
            onDismiss = viewModel::hideConfirmationDialog
        )
    }
}
