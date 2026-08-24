package com.ytone.longcare.features.identification.facecheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.features.facecapture.FaceCaptureScreen
import com.ytone.longcare.features.identification.domain.CheckFaceFailure
import com.ytone.longcare.features.identification.domain.CheckFaceOrderIdPolicy
import com.ytone.longcare.model.OrderKey

@Composable
fun DefaultFaceVerificationScreen(
    orderKey: OrderKey,
    onNavigateBack: () -> Unit,
    onVerificationSuccess: () -> Unit,
    onPhotoPrepared: ((FaceImageMetrics) -> Unit)? = null,
    viewModel: DefaultFaceVerificationViewModel = hiltViewModel(),
) {
    if (!CheckFaceOrderIdPolicy.isSupported(orderKey.orderId)) {
        val onUnsupportedOrderBack = rememberUpdatedState(onNavigateBack)
        CustomBackHandler(customAction = { onUnsupportedOrderBack.value() })
        DefaultFaceVerificationStatusScreen(
            state = DefaultFaceVerificationUiState.TerminalError(
                CheckFaceFailure.UnsupportedOrder,
            ),
            onNavigateBack = onNavigateBack,
            onRetry = onNavigateBack,
            onContinue = onNavigateBack,
        )
        return
    }

    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val currentOnPhotoPrepared = rememberUpdatedState(onPhotoPrepared)
    val onBack =
        if (uiState == DefaultFaceVerificationUiState.Success) {
            onVerificationSuccess
        } else {
            onNavigateBack
        }

    LaunchedEffect(viewModel) {
        viewModel.photoMetrics.collect { metrics ->
            currentOnPhotoPrepared.value?.invoke(metrics)
        }
    }
    CustomBackHandler(customAction = onBack)

    when (uiState) {
        is DefaultFaceVerificationUiState.Capturing -> {
            FaceCaptureScreen(
                onFaceCaptured = { bitmap -> viewModel.verifyFace(orderKey, bitmap) },
                onNavigateBack = onNavigateBack,
                title = stringResource(R.string.default_face_verification_title),
                resetToken = uiState.attempt,
            )
        }

        DefaultFaceVerificationUiState.ProcessingImage,
        DefaultFaceVerificationUiState.Verifying,
        DefaultFaceVerificationUiState.Success,
        is DefaultFaceVerificationUiState.RetryableError,
        is DefaultFaceVerificationUiState.TerminalError,
        DefaultFaceVerificationUiState.SessionInvalidated,
        -> {
            DefaultFaceVerificationStatusScreen(
                state = uiState,
                onNavigateBack = onBack,
                onRetry = viewModel::retryCapture,
                onContinue = onVerificationSuccess,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultFaceVerificationStatusScreen(
    state: DefaultFaceVerificationUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val presentation = state.toStatusPresentation()
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF2B6CB0), Color(0xFF63B3ED)),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.default_face_verification_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = singleClick(onClick = onNavigateBack)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(
                                    R.string.default_face_verification_back,
                                ),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
            bottomBar = {
                when (state) {
                    DefaultFaceVerificationUiState.Success -> {
                        DefaultFaceVerificationActionButton(
                            text = stringResource(R.string.default_face_verification_continue),
                            onClick = onContinue,
                        )
                    }

                    is DefaultFaceVerificationUiState.RetryableError -> {
                        DefaultFaceVerificationActionButton(
                            text = stringResource(R.string.default_face_verification_retry),
                            onClick = onRetry,
                        )
                    }

                    is DefaultFaceVerificationUiState.TerminalError -> {
                        DefaultFaceVerificationActionButton(
                            text = stringResource(R.string.default_face_verification_back),
                            onClick = onNavigateBack,
                        )
                    }

                    else -> Unit
                }
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        presentation.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = presentation.iconColor,
                            )
                        } ?: androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = presentation.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultFaceVerificationActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Button(
            onClick = singleClick(onClick = onClick),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text)
        }
    }
}

private data class DefaultFaceStatusPresentation(
    val title: String,
    val message: String,
    val icon: ImageVector?,
    val iconColor: Color,
)

@Composable
private fun DefaultFaceVerificationUiState.toStatusPresentation(): DefaultFaceStatusPresentation =
    when (this) {
        DefaultFaceVerificationUiState.ProcessingImage -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_processing_title),
            message = stringResource(R.string.default_face_verification_processing_message),
            icon = null,
            iconColor = MaterialTheme.colorScheme.primary,
        )

        DefaultFaceVerificationUiState.Verifying -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_verifying_title),
            message = stringResource(R.string.default_face_verification_verifying_message),
            icon = null,
            iconColor = MaterialTheme.colorScheme.primary,
        )

        DefaultFaceVerificationUiState.Success -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_success_title),
            message = stringResource(R.string.default_face_verification_success_message),
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF34C759),
        )

        is DefaultFaceVerificationUiState.RetryableError -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_error_title),
            message = failure?.displayMessage()
                ?: stringResource(R.string.face_capture_hint_photo_processing_failed),
            icon = Icons.Default.ErrorOutline,
            iconColor = MaterialTheme.colorScheme.error,
        )

        is DefaultFaceVerificationUiState.TerminalError -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_terminal_error_title),
            message = failure.displayMessage(),
            icon = Icons.Default.ErrorOutline,
            iconColor = MaterialTheme.colorScheme.error,
        )

        DefaultFaceVerificationUiState.SessionInvalidated -> DefaultFaceStatusPresentation(
            title = stringResource(R.string.default_face_verification_session_invalidated_title),
            message = stringResource(
                R.string.default_face_verification_session_invalidated_message,
            ),
            icon = Icons.Default.ErrorOutline,
            iconColor = MaterialTheme.colorScheme.error,
        )

        is DefaultFaceVerificationUiState.Capturing -> error("Capturing has its own screen")
    }

@Composable
private fun CheckFaceFailure.displayMessage(): String = when (this) {
    CheckFaceFailure.UnsupportedOrder -> stringResource(
        R.string.default_face_verification_order_id_unsupported,
    )
    CheckFaceFailure.MissingImage -> stringResource(
        R.string.identification_check_face_missing_image,
    )
    is CheckFaceFailure.Rejected -> serverMessage?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.identification_check_face_rejected)
    CheckFaceFailure.MissingRegisteredFace -> stringResource(
        R.string.identification_check_face_missing_registered_face,
    )
    CheckFaceFailure.SessionInvalidated -> stringResource(
        R.string.default_face_verification_session_invalidated_message,
    )
    CheckFaceFailure.NetworkError -> stringResource(R.string.identification_network_error)
}
