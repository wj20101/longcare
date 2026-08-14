package com.ytone.longcare.features.facecapture

import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine

/** Waits for CameraX to finish initializing without blocking the main thread. */
internal suspend fun LifecycleCameraController.awaitCameraInitialization() {
    initializationFuture.awaitCompletion()
}

/**
 * Returns usable selectors in preference order.
 *
 * Face capture prefers the front camera and falls back to the rear camera. An unfiltered
 * selector is only used when neither standard lens direction exists, which keeps external-only
 * camera devices usable.
 */
internal fun availableCameraSelectors(
    hasCamera: (CameraSelector) -> Boolean,
): List<CameraSelector> {
    val standardSelectors = listOf(
        CameraSelector.DEFAULT_FRONT_CAMERA,
        CameraSelector.DEFAULT_BACK_CAMERA,
    ).filter(hasCamera)

    if (standardSelectors.isNotEmpty()) return standardSelectors

    return listOfNotNull(ANY_CAMERA_SELECTOR.takeIf(hasCamera))
}

private suspend fun ListenableFuture<*>.awaitCompletion() {
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                val result = runCatching { get() }
                    .fold(
                        onSuccess = { Result.success(Unit) },
                        onFailure = { error ->
                            Result.failure(
                                if (error is ExecutionException) {
                                    error.cause ?: error
                                } else {
                                    error
                                },
                            )
                        },
                    )
                if (continuation.isActive) {
                    continuation.resumeWith(result)
                }
            },
            DIRECT_EXECUTOR,
        )
    }
}

private val ANY_CAMERA_SELECTOR = CameraSelector.Builder().build()
private val DIRECT_EXECUTOR = Executor(Runnable::run)
