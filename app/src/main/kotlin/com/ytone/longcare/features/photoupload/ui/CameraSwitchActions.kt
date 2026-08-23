package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.getSystemService
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.klogI
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun detectFrontCameraAvailability(
    context: Context,
    cameraController: LifecycleCameraController
): Boolean {
    var detected = false
    for (attempt in 1..3) {
        delay(200L * attempt)

        detected = try {
            val hasCamera = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            if (hasCamera) {
                true
            } else {
                val cameraManager = context.getSystemService(CameraManager::class.java)
                cameraManager?.cameraIdList?.any { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    facing == CameraCharacteristics.LENS_FACING_FRONT
                } ?: false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt == 3) {
                CameraEventTracker.trackError(
                    CameraEventTracker.EventType.CAMERA_INIT_ERROR,
                    e,
                    mapOf("reason" to "检测前置摄像头失败", "attempt" to attempt)
                )
            }
            false
        }

        if (detected) {
            break
        }
    }
    return detected
}

internal suspend fun switchCameraWithFeedback(
    context: Context,
    cameraController: LifecycleCameraController,
    wasUsingFrontCamera: Boolean,
    onSwitched: (Boolean) -> Unit
) {
    val newSelector = if (wasUsingFrontCamera) {
        CameraSelector.DEFAULT_BACK_CAMERA
    } else {
        CameraSelector.DEFAULT_FRONT_CAMERA
    }

    val targetAvailable = try {
        cameraController.hasCamera(newSelector)
    } catch (_: CameraInfoUnavailableException) {
        false
    }

    if (!targetAvailable) {
        Toast.makeText(
            context,
            context.getString(R.string.camera_target_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    cameraController.cameraSelector = newSelector
    onSwitched(!wasUsingFrontCamera)

    var waitTime = 0L
    val maxWaitTime = 1500L
    val checkInterval = 100L
    while (waitTime < maxWaitTime) {
        delay(checkInterval)
        waitTime += checkInterval
        try {
            if (cameraController.cameraInfo != null) {
                break
            }
        } catch (exception: IllegalStateException) {
            klogI("相机切换尚未完成，继续等待: ${exception.message}")
        }
    }
}

internal fun onSwitchCameraPressed(
    context: Context,
    cameraController: LifecycleCameraController,
    isCameraSwitching: Boolean,
    isCapturing: Boolean,
    isCountingDown: Boolean,
    isFrontCamera: Boolean,
    scope: CoroutineScope,
    onCountdownUpdate: (Int) -> Unit,
    onSwitchingStateChanged: (Boolean) -> Unit,
    onFrontCameraStateChanged: (Boolean) -> Unit
) {
    if (isCameraSwitching || isCapturing) {
        return
    }

    if (isCountingDown) {
        onCountdownUpdate(0)
    }

    val wasUsingFrontCamera = isFrontCamera
    scope.launch {
        try {
            onSwitchingStateChanged(true)
            switchCameraWithFeedback(
                context = context,
                cameraController = cameraController,
                wasUsingFrontCamera = wasUsingFrontCamera,
                onSwitched = onFrontCameraStateChanged
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.CAMERA_SWITCH_ERROR,
                e
            )
            Toast.makeText(
                context,
                context.getString(R.string.camera_switch_failed),
                Toast.LENGTH_SHORT,
            ).show()
        } finally {
            onSwitchingStateChanged(false)
        }
    }
}
