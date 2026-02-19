package com.ytone.longcare.features.photoupload.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.model.WatermarkData

@Composable
internal fun CameraContentOverlayScaffold(
    padding: PaddingValues,
    watermarkData: WatermarkData,
    time: String,
    location: String,
    logoImg: String,
    delayMode: DelayMode,
    isCapturing: Boolean,
    isCountingDown: Boolean,
    hasFrontCamera: Boolean,
    isCameraSwitching: Boolean,
    countdownSeconds: Int,
    onWatermarkViewReady: (View) -> Unit,
    onToggleDelayMode: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCameraClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        CameraWatermarkOverlay(
            watermarkData = watermarkData,
            time = time,
            location = location,
            logoImg = logoImg,
            onViewReady = onWatermarkViewReady,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 13.dp, bottom = 14.dp)
        )

        CameraTopToolbar(
            delayMode = delayMode,
            enabled = !isCapturing && !isCountingDown,
            onToggleDelayMode = onToggleDelayMode
        )

        CameraBottomControlBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hasFrontCamera = hasFrontCamera,
            isCameraSwitching = isCameraSwitching,
            isCapturing = isCapturing,
            isCountingDown = isCountingDown,
            onShutterClick = onShutterClick,
            onSwitchCameraClick = onSwitchCameraClick
        )

        CameraCountdownOverlay(
            countdownSeconds = countdownSeconds,
            visible = isCountingDown
        )

        CameraCapturingOverlay(visible = isCapturing && !isCountingDown)
    }
}
