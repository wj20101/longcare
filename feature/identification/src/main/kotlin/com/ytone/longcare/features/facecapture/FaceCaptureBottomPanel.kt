package com.ytone.longcare.features.facecapture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.feature.identification.R
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun FaceCaptureBottomPanel(
    uiState: FaceCaptureUiState,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.confirmationProgress,
        animationSpec = tween(durationMillis = 160),
        label = "face_confirmation_progress",
    )

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = uiState.userHint,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) + slideInVertically() togetherWith
                    fadeOut(animationSpec = tween(300)) + slideOutVertically()
            },
            label = "face_capture_hint",
        ) { hint ->
            Text(
                text = stringResource(hint.messageRes),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        when (uiState.phase) {
            FaceCapturePhase.STARTING -> FaceCaptureStatusRow(
                icon = Icons.Default.CameraAlt,
                text = stringResource(R.string.face_capture_starting_camera),
                tint = Color.LightGray,
            )

            FaceCapturePhase.PREPARING -> FaceCaptureStatusRow(
                icon = Icons.Default.Timer,
                text = stringResource(R.string.face_capture_wait_for_countdown),
                tint = Color.White,
            )

            FaceCapturePhase.SCANNING -> FaceCaptureStatusRow(
                icon = Icons.Default.CameraAlt,
                text = if (uiState.faceDetected) {
                    stringResource(R.string.face_capture_checking_quality)
                } else {
                    stringResource(R.string.face_capture_searching)
                },
                tint = if (uiState.faceDetected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.LightGray
                },
            )

            FaceCapturePhase.CONFIRMING -> {
                val progressText = stringResource(
                    R.string.face_capture_confirming_progress,
                    (animatedProgress * 100).roundToInt(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = progressText },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(0xFF34C759),
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = progressText,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            FaceCapturePhase.CAPTURING -> FaceCaptureStatusRow(
                icon = Icons.Default.CameraAlt,
                text = stringResource(R.string.face_capture_capturing),
                tint = Color(0xFF34C759),
            )

            FaceCapturePhase.CAPTURED -> FaceCaptureStatusRow(
                icon = Icons.Default.CheckCircle,
                text = stringResource(R.string.face_capture_success),
                tint = Color(0xFF34C759),
            )
        }
    }
}

@Composable
private fun FaceCaptureStatusRow(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = text
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
        )
    }
}
