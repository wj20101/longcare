package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
internal fun rememberCameraLocationPermissions(): Array<String> {
    return remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

@Composable
internal fun rememberLocationPermissionLauncher(
    viewModel: CameraViewModel
): ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                viewModel.updateCurrentLocationInfo()
            }
        }
    )
}

@Composable
internal fun ObserveCameraResume(
    lifecycleOwner: LifecycleOwner,
    viewModel: CameraViewModel,
    launcher: ActivityResultLauncher<Array<String>>,
    locationPermissions: Array<String>
) {
    DisposableEffect(lifecycleOwner, viewModel, launcher, locationPermissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateTime()
                viewModel.updateSyLogoImg()
                launcher.launch(locationPermissions)
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
