package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CameraTopToolbar(
    delayMode: DelayMode,
    enabled: Boolean,
    onToggleDelayMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        DelayTimerButton(
            currentMode = delayMode,
            onClick = onToggleDelayMode,
            enabled = enabled
        )
    }
}

@Composable
internal fun CameraBottomControlBar(
    modifier: Modifier = Modifier,
    hasFrontCamera: Boolean,
    isCameraSwitching: Boolean,
    isCapturing: Boolean,
    isCountingDown: Boolean,
    onShutterClick: () -> Unit,
    onSwitchCameraClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp))

        ShutterButton(
            onClick = onShutterClick,
            enabled = !isCameraSwitching && (!isCapturing || isCountingDown),
            isCountingDown = isCountingDown
        )

        if (hasFrontCamera) {
            CameraSwitchButton(
                onClick = onSwitchCameraClick,
                enabled = !isCameraSwitching && !isCapturing
            )
        } else {
            Box(modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
internal fun CameraCountdownOverlay(
    countdownSeconds: Int,
    visible: Boolean,
) {
    if (!visible) return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = countdownSeconds.toString(),
            style = TextStyle(
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    blurRadius = 8f
                ),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
internal fun CameraCapturingOverlay(visible: Boolean) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}
