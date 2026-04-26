package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.View
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.WatermarkData
import java.io.File

@Composable
internal fun CameraContent(
    context: Context,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel,
    onImageCaptured: (File) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = rememberCameraLifecycleController(context)
    var watermarkView by remember { mutableStateOf<View?>(null) }
    val location by viewModel.location.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val logoImg by viewModel.syLogoImg.collectAsStateWithLifecycle()

    var isFrontCamera by remember { mutableStateOf(false) }
    var isCameraSwitching by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cameraController) {
        hasFrontCamera = detectFrontCameraAvailability(context, cameraController)
    }

    ObserveCameraResume(
        context = context,
        lifecycleOwner = lifecycleOwner,
        viewModel = viewModel
    )

    var delayMode by remember { mutableStateOf(DelayMode.OFF) }
    var countdownSeconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val isCountingDown = countdownSeconds > 0

    val performCapture: () -> Unit = {
        startWatermarkCapture(
            context = context,
            cameraController = cameraController,
            watermarkView = watermarkView,
            isFrontCamera = isFrontCamera,
            scope = scope,
            preparingMessage = "水印准备中，请稍后...",
            onCaptureStarted = {
                isCapturing = true
                viewModel.updateTime()
            },
            onCaptureFinished = { isCapturing = false },
            onImageCaptured = onImageCaptured
        )
    }

    HandleCaptureCountdown(
        countdownSeconds = countdownSeconds,
        onCountdownTick = { countdownSeconds = it },
        onCountdownFinished = performCapture
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            cameraController = cameraController,
            lifecycleOwner = lifecycleOwner,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { padding ->
            CameraContentOverlayScaffold(
                padding = padding,
                watermarkData = watermarkData,
                time = time,
                location = location,
                logoImg = logoImg,
                delayMode = delayMode,
                isCapturing = isCapturing,
                isCountingDown = isCountingDown,
                hasFrontCamera = hasFrontCamera,
                isCameraSwitching = isCameraSwitching,
                countdownSeconds = countdownSeconds,
                onWatermarkViewReady = { watermarkView = it },
                onToggleDelayMode = { delayMode = delayMode.next() },
                onShutterClick = {
                    onShutterPressed(
                        context = context,
                        cameraController = cameraController,
                        watermarkView = watermarkView,
                        delayMode = delayMode,
                        isCountingDown = isCountingDown,
                        isCameraSwitching = isCameraSwitching,
                        isCapturing = isCapturing,
                        isFrontCamera = isFrontCamera,
                        scope = scope,
                        onCountdownUpdate = { countdownSeconds = it },
                        onCaptureStateChanged = { isCapturing = it },
                        onBeforeCapture = { viewModel.updateTime() },
                        onImageCaptured = onImageCaptured
                    )
                },
                onSwitchCameraClick = {
                    onSwitchCameraPressed(
                        context = context,
                        cameraController = cameraController,
                        isCameraSwitching = isCameraSwitching,
                        isCapturing = isCapturing,
                        isCountingDown = isCountingDown,
                        isFrontCamera = isFrontCamera,
                        scope = scope,
                        onCountdownUpdate = { countdownSeconds = it },
                        onSwitchingStateChanged = { isCameraSwitching = it },
                        onFrontCameraStateChanged = { isFrontCamera = it }
                    )
                }
            )
        }
    }
}
