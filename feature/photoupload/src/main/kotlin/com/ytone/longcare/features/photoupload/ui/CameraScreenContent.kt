package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.feature.photoupload.R
import com.ytone.longcare.feature.photoupload.databinding.WatermarkViewBinding
import com.ytone.longcare.features.photoupload.vm.CameraLocationState
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.WatermarkData
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun CameraContent(
    context: Context,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel,
    onImageCaptured: (File) -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = rememberCameraLifecycleController(context)
    var preview by remember { mutableStateOf<WatermarkViewBinding?>(null) }
    val locationState by viewModel.location.collectAsStateWithLifecycle()
    val location = when (val state = locationState) {
        CameraLocationState.Loading -> stringResource(R.string.camera_location_loading)
        CameraLocationState.Unavailable -> stringResource(R.string.camera_location_unavailable)
        CameraLocationState.Failed -> stringResource(R.string.camera_location_failed)
        is CameraLocationState.Coordinates -> state.value
    }
    val time by viewModel.time.collectAsStateWithLifecycle()
    val logoImg by viewModel.syLogoImg.collectAsStateWithLifecycle()
    val onLocationFailure = rememberCameraLocationFailureHandler()
    val scope = rememberCoroutineScope()
    val returnImage by rememberUpdatedState(onImageCaptured)
    var isFrontCamera by remember { mutableStateOf(false) }
    var isCameraSwitching by remember { mutableStateOf(false) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var delayMode by remember { mutableStateOf(DelayMode.OFF) }
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var shutterJob by remember { mutableStateOf<Job?>(null) }
    var shutterDispatched by remember { mutableStateOf(false) }

    LaunchedEffect(cameraController) {
        hasFrontCamera = detectFrontCameraAvailability(context, cameraController)
    }
    LaunchedEffect(viewModel) {
        viewModel.awaitAccountChange()
        shutterJob?.cancel()
    }
    LifecycleResumeEffect(viewModel, watermarkData) {
        viewModel.onCameraResume(UnifiedPermissionHelper.hasLocationPermission(context))
        preview?.logoImageView?.updateWatermarkLogo(logoImg, retryFailed = true)
        onPauseOrDispose {
            if (!shutterDispatched) shutterJob?.cancel()
            countdownSeconds = 0
            viewModel.onCameraPause()
        }
    }

    fun onShutterClick() {
        if (countdownSeconds > 0) {
            shutterJob?.cancel()
            return
        }
        if (shutterJob != null || isCameraSwitching || lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED) return
        val binding = preview
        if (binding == null || binding.root.width == 0 || binding.root.height == 0) {
            Toast.makeText(context, R.string.camera_watermark_preparing, Toast.LENGTH_SHORT).show()
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            viewModel.awaitCountdown(delayMode.seconds) { countdownSeconds = it }
            takePhoto(
                context = context,
                watermarkData = watermarkData,
                previewLogo = binding.logoImageView,
                isFrontCamera = isFrontCamera,
                lifecycle = lifecycleOwner.lifecycle,
                captureImage = { file, prepareWatermark ->
                    cameraController.saveLocatedImage(
                        context, file, viewModel, prepareWatermark,
                        onDispatched = { shutterDispatched = true },
                        onLocationFailure = onLocationFailure,
                    )
                },
                processCapturedImage = viewModel::processCapturedImage,
                deliver = { file -> viewModel.isCurrentAccount() && returnImage(file) },
            )
        }
        shutterJob = job
        job.invokeOnCompletion {
            if (shutterJob === job) {
                shutterDispatched = false
                shutterJob = null
                countdownSeconds = 0
            }
        }
        job.start()
    }

    val isCountingDown = countdownSeconds > 0
    val isCapturing = shutterJob != null && !isCountingDown
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            cameraController = cameraController,
            lifecycleOwner = lifecycleOwner,
            modifier = Modifier.fillMaxSize()
        )
        Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { padding ->
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
                onWatermarkViewReady = { preview = it },
                onToggleDelayMode = { delayMode = delayMode.next() },
                onShutterClick = ::onShutterClick,
                onSwitchCameraClick = {
                    onSwitchCameraPressed(
                        context = context,
                        cameraController = cameraController,
                        isCameraSwitching = isCameraSwitching,
                        isCapturing = isCapturing,
                        isCountingDown = isCountingDown,
                        isFrontCamera = isFrontCamera,
                        scope = scope,
                        onCountdownUpdate = { shutterJob?.cancel() },
                        onSwitchingStateChanged = { isCameraSwitching = it },
                        onFrontCameraStateChanged = { isFrontCamera = it }
                    )
                }
            )
        }
    }
}
