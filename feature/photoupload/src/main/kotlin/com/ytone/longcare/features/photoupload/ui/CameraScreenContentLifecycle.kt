package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
