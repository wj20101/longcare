package com.ytone.longcare.features.photoupload.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import coil3.load
import coil3.request.allowHardware
import coil3.request.error
import com.ytone.longcare.R
import com.ytone.longcare.databinding.WatermarkViewBinding
import com.ytone.longcare.model.WatermarkData

@Composable
internal fun CameraWatermarkOverlay(
    watermarkData: WatermarkData,
    time: String,
    location: String,
    logoImg: String,
    onViewReady: (View) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            val binding = WatermarkViewBinding.inflate(
                LayoutInflater.from(ctx),
                FrameLayout(ctx),
                false
            )
            val view = binding.root
            view.tag = binding
            onViewReady(view)
            view
        },
        update = { view ->
            val binding = view.tag as WatermarkViewBinding
            binding.serviceTypeTextView.text = watermarkData.title
            binding.insuredPersonTextView.text = watermarkData.insuredPerson
            binding.caregiverTextView.text = watermarkData.caregiver
            binding.captureTimeTextView.text = time
            binding.coordinatesTextView.text = location
            binding.captureLocationTextView.text = watermarkData.address
            if (logoImg.isNotEmpty()) {
                binding.logoImageView.load(logoImg) {
                    allowHardware(false)
                    error(R.drawable.app_watermark_image)
                }
            }
        },
        modifier = modifier
    )
}
