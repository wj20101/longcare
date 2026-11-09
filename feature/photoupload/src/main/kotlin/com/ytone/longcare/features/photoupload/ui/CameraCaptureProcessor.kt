package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.feature.photoupload.R
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import com.ytone.longcare.features.photoupload.vm.CaptureStamp
import com.ytone.longcare.model.WatermarkData
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun takePhoto(
    context: Context,
    watermarkData: WatermarkData,
    previewLogo: ImageView?,
    isFrontCamera: Boolean,
    lifecycle: Lifecycle,
    captureImage: suspend (File, prepareWatermark: () -> Boolean) -> CaptureStamp?,
    processCapturedImage: suspend (WatermarkedCaptureRequest) -> File,
    deliver: (File) -> Boolean,
) {
    var undelivered: File? = null
    var captured = false
    val caller = currentCoroutineContext()
    try {
        val input = File.createTempFile("temp_capture_", ".jpg", context.cacheDir)
        var logo: Bitmap? = null
        var watermark: Bitmap? = null
        try {
            val stamp = captureImage(input) {
                logo?.recycle()
                val image = previewLogo?.takeIf { it.drawable != null }
                logo = image?.let(::viewToBitmapSafe)
                val ready = image == null || logo != null
                if (!ready) Toast.makeText(context, R.string.camera_watermark_failed, Toast.LENGTH_SHORT).show()
                ready
            } ?: return
            captured = true
            val bitmap = renderCaptureWatermark(context, watermarkData, stamp, logo)
            if (bitmap == null) {
                Toast.makeText(context, R.string.camera_watermark_failed, Toast.LENGTH_SHORT).show()
                return
            }
            watermark = bitmap
            val density = context.resources.displayMetrics.density
            caller.ensureActive()
            // Take ownership of the output even if the page exits as the encoder returns from IO.
            withContext(NonCancellable) {
                undelivered = processCapturedImage(WatermarkedCaptureRequest(input, bitmap, 13 * density, 14 * density, isFrontCamera))
            }
        } finally {
            input.delete()
            logo?.recycle()
            watermark?.let { if (!it.isRecycled) it.recycle() }
        }

        val file = requireNotNull(undelivered)
        caller.ensureActive()
        lifecycle.withResumed {
            caller.ensureActive()
            // Returning the photo can pop this page. Transfer ownership inside the same callback.
            if (deliver(file)) undelivered = null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val event = if (captured) CameraEventTracker.EventType.IMAGE_PROCESS_ERROR else CameraEventTracker.EventType.CAPTURE_ERROR
        CameraEventTracker.trackError(event, error)
        caller.ensureActive()
        val message = if (captured) R.string.camera_image_processing_failed else R.string.camera_capture_save_failed
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    } finally {
        undelivered?.delete()
    }
}
