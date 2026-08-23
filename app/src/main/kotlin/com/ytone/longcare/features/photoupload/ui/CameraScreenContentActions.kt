package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.ytone.longcare.R
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal fun startWatermarkCapture(
    context: Context,
    cameraController: LifecycleCameraController,
    watermarkView: View?,
    isFrontCamera: Boolean,
    scope: CoroutineScope,
    processCapturedImage: suspend (WatermarkedCaptureRequest) -> File,
    preparingMessage: String,
    onCaptureStarted: () -> Unit,
    onCaptureFinished: () -> Unit,
    onImageCaptured: (File) -> Unit
): Boolean {
    val view = watermarkView
    if (view == null || view.width <= 0 || view.height <= 0) {
        Toast.makeText(context, preparingMessage, Toast.LENGTH_SHORT).show()
        return false
    }

    onCaptureStarted()
    takePhoto(
        context = context,
        cameraController = cameraController,
        executor = ContextCompat.getMainExecutor(context),
        watermarkView = view,
        isFrontCamera = isFrontCamera,
        scope = scope,
        processCapturedImage = processCapturedImage,
        onImageCaptured = { file ->
            onCaptureFinished()
            onImageCaptured(file)
        },
        onError = onCaptureFinished
    )
    return true
}

internal fun onShutterPressed(
    context: Context,
    cameraController: LifecycleCameraController,
    watermarkView: View?,
    delayMode: DelayMode,
    isCountingDown: Boolean,
    isCameraSwitching: Boolean,
    isCapturing: Boolean,
    isFrontCamera: Boolean,
    scope: CoroutineScope,
    processCapturedImage: suspend (WatermarkedCaptureRequest) -> File,
    onCountdownUpdate: (Int) -> Unit,
    onCaptureStateChanged: (Boolean) -> Unit,
    onBeforeCapture: () -> Unit,
    onImageCaptured: (File) -> Unit
) {
    if (isCountingDown) {
        onCountdownUpdate(0)
        return
    }
    if (isCameraSwitching || isCapturing) {
        return
    }

    val view = watermarkView
    if (view == null || view.width <= 0 || view.height <= 0) {
        Toast.makeText(
            context,
            context.getString(R.string.camera_preparing),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    if (delayMode != DelayMode.OFF) {
        onCountdownUpdate(delayMode.seconds)
        return
    }

    startWatermarkCapture(
        context = context,
        cameraController = cameraController,
        watermarkView = view,
        isFrontCamera = isFrontCamera,
        scope = scope,
        processCapturedImage = processCapturedImage,
        preparingMessage = context.getString(R.string.camera_preparing),
        onCaptureStarted = {
            onCaptureStateChanged(true)
            onBeforeCapture()
        },
        onCaptureFinished = { onCaptureStateChanged(false) },
        onImageCaptured = onImageCaptured
    )
}
