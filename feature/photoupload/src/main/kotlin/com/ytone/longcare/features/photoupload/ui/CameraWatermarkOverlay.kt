package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import coil3.load
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.allowHardware
import coil3.request.error
import coil3.request.placeholder
import coil3.result
import com.ytone.longcare.feature.photoupload.R
import com.ytone.longcare.feature.photoupload.databinding.WatermarkViewBinding
import com.ytone.longcare.features.photoupload.vm.CaptureStamp
import com.ytone.longcare.features.photoupload.vm.watermarkCoordinates
import com.ytone.longcare.model.WatermarkData
import kotlin.math.roundToInt

internal fun inflateWatermark(context: Context): WatermarkViewBinding {
    val binding = WatermarkViewBinding.inflate(LayoutInflater.from(context), FrameLayout(context), false)
    val metrics = context.resources.displayMetrics
    val available = (metrics.widthPixels - 26 * metrics.density).roundToInt().coerceAtLeast(1)
    val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    binding.root.layoutParams.width = (binding.root.layoutParams.width * fontScale).roundToInt().coerceAtMost(available)
    return binding
}

@Composable
internal fun CameraWatermarkOverlay(
    watermarkData: WatermarkData,
    time: String,
    location: String,
    logoImg: String,
    onViewReady: (WatermarkViewBinding) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val binding = inflateWatermark(context)
            binding.root.tag = binding
            onViewReady(binding)
            binding.root
        },
        update = { view ->
            val binding = view.tag as WatermarkViewBinding
            bindCameraWatermark(binding, watermarkData, time, location)
            binding.logoImageView.updateWatermarkLogo(logoImg)
        },
        modifier = modifier,
    )
}

internal fun ImageView.updateWatermarkLogo(logoImg: String, retryFailed: Boolean = false) {
    val url = logoImg.takeIf { it.isNotEmpty() }
    val retry = retryFailed && url != null && (result as? ErrorResult)?.request?.data == url
    if (tag == url && !retry) return
    tag = url
    load(url) {
        allowHardware(false)
        if (url != null) error(R.drawable.app_watermark_image)
        if (retry) {
            diskCachePolicy(CachePolicy.WRITE_ONLY)
            placeholder(R.drawable.app_watermark_image)
        }
    }
}

internal fun bindCameraWatermark(
    binding: WatermarkViewBinding,
    watermarkData: WatermarkData,
    time: String,
    location: String,
) {
    binding.serviceTypeTextView.text = watermarkData.title
    binding.insuredPersonField.isVisible = watermarkData.insuredPerson.isNotBlank()
    binding.insuredPersonTextView.text = watermarkData.insuredPerson
    binding.caregiverTextView.text = watermarkData.caregiver
    binding.captureTimeTextView.text = time
    // Prefer wrapping between longitude and latitude while preserving every printed digit.
    binding.coordinatesTextView.text = location.replace(",", ",\u200B")
    binding.addressRow.isVisible = watermarkData.address.isNotBlank()
    binding.captureLocationTextView.text = watermarkData.address
}

internal fun renderCaptureWatermark(
    context: Context,
    watermarkData: WatermarkData,
    stamp: CaptureStamp,
    logo: Bitmap?,
): Bitmap? {
    val binding = inflateWatermark(context)
    bindCameraWatermark(binding, watermarkData, stamp.time, stamp.location.watermarkCoordinates())
    binding.logoImageView.setImageBitmap(logo)
    val view = binding.root
    // The preview may still have the preceding time's line count. Measure the final strings together.
    view.measure(
        View.MeasureSpec.makeMeasureSpec(view.layoutParams.width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    return viewToBitmapSafe(view)
}
