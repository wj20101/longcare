package com.ytone.longcare.assistant

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationScreen
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationUiState
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationViewModel
import com.ytone.longcare.features.identification.facecheck.FaceImageMetrics
import com.ytone.longcare.model.OrderKey

internal enum class AssistantFaceOutcome(@param:StringRes val messageRes: Int) {
    SUCCESS(R.string.assistant_face_success),
    FAILED(R.string.assistant_face_failed),
    CANCELLED(R.string.assistant_face_cancelled),
}

internal fun faceOutcomeOnExit(state: DefaultFaceVerificationUiState) = when (state) {
    DefaultFaceVerificationUiState.Success -> AssistantFaceOutcome.SUCCESS
    is DefaultFaceVerificationUiState.RetryableError,
    is DefaultFaceVerificationUiState.TerminalError,
    DefaultFaceVerificationUiState.SessionInvalidated -> AssistantFaceOutcome.FAILED
    else -> AssistantFaceOutcome.CANCELLED
}

@Composable
internal fun AssistantDefaultFaceScreen(
    orderId: Long,
    onNavigateBack: () -> Unit,
    onPhotoPrepared: (FaceImageMetrics) -> Unit,
    onOutcome: (AssistantFaceOutcome) -> Unit,
    viewModel: DefaultFaceVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DefaultFaceVerificationScreen(
        orderKey = OrderKey(orderId), viewModel = viewModel, onPhotoPrepared = onPhotoPrepared,
        onNavigateBack = { onOutcome(faceOutcomeOnExit(state)); onNavigateBack() },
        onVerificationSuccess = { onOutcome(AssistantFaceOutcome.SUCCESS); onNavigateBack() },
    )
}
