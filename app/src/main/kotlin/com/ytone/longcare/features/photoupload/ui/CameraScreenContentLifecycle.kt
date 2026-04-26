package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.content.ContextCompat
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import kotlinx.coroutines.delay
import android.util.Size

@Composable
internal fun rememberCameraLifecycleController(context: Context): LifecycleCameraController {
    return remember {
        LifecycleCameraController(context).apply {
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            imageCaptureResolutionSelector = resolutionSelector
        }
    }
}

@Composable
internal fun ObserveCameraResume(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    viewModel: CameraViewModel
) {
    DisposableEffect(context, lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateTime()
                viewModel.updateSyLogoImg()
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.updateCurrentLocationInfo()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
internal fun HandleCaptureCountdown(
    countdownSeconds: Int,
    onCountdownTick: (Int) -> Unit,
    onCountdownFinished: () -> Unit
) {
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            val next = countdownSeconds - 1
            onCountdownTick(next)
            if (next == 0) {
                onCountdownFinished()
            }
        }
    }
}
