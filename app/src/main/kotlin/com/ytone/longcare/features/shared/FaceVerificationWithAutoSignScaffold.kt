package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FaceVerificationWithAutoSignScaffold(
    onNavigateBack: () -> Unit,
    showSnackbar: Boolean,
    snackbarMessage: String,
    onDismissSnackbar: () -> Unit,
    capturedPhoto: Bitmap?,
    isProcessingPhoto: Boolean,
    onRetakePhoto: () -> Unit,
    onStartCapture: () -> Unit,
    sourcePhotoBase64: String?,
    uiState: FaceVerificationViewModel.FaceVerifyUiState,
    currentUserId: String?,
    onStartVerification: () -> Unit,
    onResetAll: () -> Unit,
    onClearError: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能人脸验证") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            if (showSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = onDismissSnackbar) {
                            Text("确定")
                        }
                    }
                ) {
                    Text(snackbarMessage)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AutoSignInstructionsCard()

            FacePhotoCaptureCard(
                capturedPhoto = capturedPhoto,
                isProcessingPhoto = isProcessingPhoto,
                onRetakePhoto = onRetakePhoto,
                onStartCapture = onStartCapture
            )

            if (sourcePhotoBase64 != null) {
                FaceVerificationStepCard(
                    uiState = uiState,
                    currentUserId = currentUserId,
                    onStartVerification = onStartVerification,
                    onResetAll = onResetAll,
                    onClearError = onClearError
                )
            }
        }
    }
}
