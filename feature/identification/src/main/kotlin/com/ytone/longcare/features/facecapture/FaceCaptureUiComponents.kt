package com.ytone.longcare.features.facecapture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.feature.identification.R

@Composable
internal fun FaceDetectionGuide(
    uiState: FaceCaptureUiState,
    modifier: Modifier = Modifier,
) {
    val targetColor = when (uiState.phase) {
        FaceCapturePhase.STARTING,
        FaceCapturePhase.PREPARING -> Color.White.copy(alpha = 0.75f)
        FaceCapturePhase.SCANNING -> when {
            !uiState.faceDetected -> Color.White.copy(alpha = 0.75f)
            uiState.faceQuality > 0.6f -> Color(0xFFFFC107)
            else -> Color(0xFFFF6B6B)
        }

        FaceCapturePhase.CONFIRMING -> lerp(
            start = Color(0xFFFFC107),
            stop = Color(0xFF34C759),
            fraction = uiState.confirmationProgress,
        )

        FaceCapturePhase.CAPTURED -> Color(0xFF34C759)
    }
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 180),
        label = "face_guide_color",
    )

    Box(
        modifier = modifier
            .size(width = 240.dp, height = 300.dp)
            .border(
                width = 4.dp,
                color = guideColor,
                shape = RoundedCornerShape(72.dp),
            ),
    )
}

@Composable
internal fun FacePreparationCountdown(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    val countdownDescription = stringResource(
        R.string.face_capture_countdown_description,
        seconds,
    )
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = countdownDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.face_capture_prepare_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (scaleIn(animationSpec = tween(220)) + fadeIn()) togetherWith
                        (scaleOut(animationSpec = tween(180)) + fadeOut())
                },
                label = "face_capture_countdown",
            ) { currentSeconds ->
                Text(
                    text = currentSeconds.toString(),
                    color = Color.White,
                    fontSize = 60.sp,
                    lineHeight = 68.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
