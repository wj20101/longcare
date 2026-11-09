package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.features.photoupload.vm.CaptureStamp
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun LifecycleCameraController.saveLocatedImage(
    context: Context,
    file: File,
    viewModel: CameraViewModel,
    prepareWatermark: () -> Boolean,
    onDispatched: () -> Unit,
    onLocationFailure: (LocationFailure) -> Unit,
): CaptureStamp? {
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor = ContextCompat.getMainExecutor(context)
    val stamp = viewModel.shutterStamp(prepareWatermark, onLocationFailure) ?: return null
    suspendCancellableCoroutine<Unit> { continuation ->
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(Unit) else file.delete()
            }

            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) continuation.resumeWithException(exception) else file.delete()
            }
        }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        viewModel.ensureCameraSession()
        takePicture(options, executor, callback)
        onDispatched()
    }
    return stamp
}
