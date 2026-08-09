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
import com.ytone.longcare.platform.face.rememberFaceSdkUiController

@Composable
fun FaceVerificationWithAutoSignScreen(
    onNavigateBack: () -> Unit,
    onVerificationSuccess: (FaceVerifyResult) -> Unit,
    viewModel: FaceVerificationViewModel = hiltViewModel()
) {
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sdkLaunchRequest by viewModel.sdkLaunchRequest.collectAsStateWithLifecycle()
    val photoProcessingState by viewModel.photoProcessingState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()

    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var sourcePhotoBase64 by remember { mutableStateOf<String?>(null) }
    var showFaceCapture by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val faceSdkUiController = rememberFaceSdkUiController()
    val currentUserId = user?.userId?.toString()

    val showMessage: (String) -> Unit = { message ->
        snackbarMessage = message
        showSnackbar = true
    }

    val startVerification = {
        startAutoSignVerification(
            sourcePhotoBase64 = sourcePhotoBase64,
            currentUserId = currentUserId,
            viewModel = viewModel,
            onShowMessage = showMessage
        )
    }

    val handleFaceCaptured = { imagePath: String ->
        viewModel.processCapturedPhoto(imagePath)
    }

    LaunchedEffect(uiState) {
        consumeFaceVerifyUiState(
            uiState = uiState,
            onShowMessage = showMessage,
            onVerificationSuccess = onVerificationSuccess
        )
    }

    LaunchedEffect(photoProcessingState) {
        when (val state = photoProcessingState) {
            is FaceVerificationViewModel.PhotoProcessingState.Success -> {
                capturedPhoto = state.photo.bitmap
                sourcePhotoBase64 = state.photo.base64
                showFaceCapture = false
                viewModel.clearPhotoProcessingState()
            }

            is FaceVerificationViewModel.PhotoProcessingState.Error -> {
                showMessage(state.message)
                viewModel.clearPhotoProcessingState()
            }

            FaceVerificationViewModel.PhotoProcessingState.Idle,
            FaceVerificationViewModel.PhotoProcessingState.Processing -> Unit
        }
    }

    LaunchedEffect(sdkLaunchRequest?.id) {
        val launchRequest = sdkLaunchRequest ?: return@LaunchedEffect
        faceSdkUiController.start(
            context = context,
            config = launchRequest.config,
            request = launchRequest.request,
            onEvent = { event -> viewModel.onFaceSdkEvent(launchRequest.id, event) },
        )
        viewModel.consumeSdkLaunchRequest(launchRequest.id)
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
        isProcessingPhoto =
            photoProcessingState is FaceVerificationViewModel.PhotoProcessingState.Processing,
        onRetakePhoto = {
            viewModel.clearPhotoProcessingState()
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
