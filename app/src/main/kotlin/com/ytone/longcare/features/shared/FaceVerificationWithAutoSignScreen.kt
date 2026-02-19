package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

@Composable
fun FaceVerificationWithAutoSignScreen(
    onNavigateBack: () -> Unit,
    onVerificationSuccess: (FaceVerifyResult) -> Unit,
    viewModel: FaceVerificationViewModel = hiltViewModel()
) {
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var sourcePhotoBase64 by remember { mutableStateOf<String?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var showFaceCapture by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentUserId = user?.userId?.toString()

    val showMessage: (String) -> Unit = { message ->
        snackbarMessage = message
        showSnackbar = true
    }

    val startVerification = {
        startAutoSignVerification(
            sourcePhotoBase64 = sourcePhotoBase64,
            currentUserId = currentUserId,
            context = context,
            viewModel = viewModel,
            onShowMessage = showMessage
        )
    }

    val handleFaceCaptured = { imagePath: String ->
        isProcessingPhoto = true
        processCapturedFacePhoto(
            imagePath = imagePath,
            onSuccess = { processed ->
                capturedPhoto = processed.bitmap
                sourcePhotoBase64 = processed.base64
                showFaceCapture = false
            },
            onError = showMessage
        )
        isProcessingPhoto = false
    }

    LaunchedEffect(uiState) {
        consumeFaceVerifyUiState(
            uiState = uiState,
            onShowMessage = showMessage,
            onVerificationSuccess = onVerificationSuccess
        )
    }

    if (showFaceCapture) {
        ManualFaceCaptureScreen(
            onNavigateBack = { showFaceCapture = false },
            onFaceCaptured = handleFaceCaptured
        )
        return
    }

    FaceVerificationWithAutoSignScaffold(
        onNavigateBack = onNavigateBack,
        showSnackbar = showSnackbar,
        snackbarMessage = snackbarMessage,
        onDismissSnackbar = { showSnackbar = false },
        capturedPhoto = capturedPhoto,
        isProcessingPhoto = isProcessingPhoto,
        onRetakePhoto = {
            capturedPhoto = null
            sourcePhotoBase64 = null
        },
        onStartCapture = { showFaceCapture = true },
        sourcePhotoBase64 = sourcePhotoBase64,
        uiState = uiState,
        currentUserId = currentUserId,
        onStartVerification = startVerification,
        onResetAll = {
            viewModel.resetState()
            capturedPhoto = null
            sourcePhotoBase64 = null
        },
        onClearError = viewModel::clearError
    )
}
