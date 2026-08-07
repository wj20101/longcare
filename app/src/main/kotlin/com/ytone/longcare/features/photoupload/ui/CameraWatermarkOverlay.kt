package com.ytone.longcare.features.photoupload.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
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
            bindCameraWatermark(
                binding = binding,
                watermarkData = watermarkData,
                time = time,
                location = location,
                logoImg = logoImg,
            )
        },
        modifier = modifier,
    )
}

internal fun bindCameraWatermark(
    binding: WatermarkViewBinding,
    watermarkData: WatermarkData,
    time: String,
    location: String,
    logoImg: String,
) {
    val showInsuredPerson = watermarkData.insuredPerson.isNotBlank()
    val showCaptureAddress = watermarkData.address.isNotBlank()

    binding.serviceTypeTextView.text = watermarkData.title
    binding.insuredPersonTextView.text = watermarkData.insuredPerson
    binding.insuredPersonLabelTextView.isVisible = showInsuredPerson
    binding.insuredPersonTextView.isVisible = showInsuredPerson
    binding.caregiverTextView.text = watermarkData.caregiver
    binding.captureTimeTextView.text = time
    binding.coordinatesTextView.text = location
    binding.captureLocationTextView.text = watermarkData.address
    binding.captureLocationLabelTextView.isVisible = showCaptureAddress
    binding.captureLocationTextView.isVisible = showCaptureAddress

    binding.coordinatesLabelTextView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        topToBottom =
            if (showCaptureAddress) {
                binding.captureLocationLabelTextView.id
            } else {
                binding.captureTimeLabelTextView.id
            }
    }

    binding.caregiverLabelTextView.updateLayoutParams<ConstraintLayout.LayoutParams> {
        if (showInsuredPerson) {
            startToStart = ConstraintSet.UNSET
            startToEnd = binding.insuredPersonTextView.id
            marginStart = binding.root.resources.displayMetrics.density.dpToPx(8)
        } else {
            startToEnd = ConstraintSet.UNSET
            startToStart = ConstraintSet.PARENT_ID
            marginStart = binding.root.resources.displayMetrics.density.dpToPx(10)
        }
    }

    if (logoImg.isNotEmpty()) {
        binding.logoImageView.load(logoImg) {
            allowHardware(false)
            error(R.drawable.app_watermark_image)
        }
    }
}

private fun Float.dpToPx(value: Int): Int = (value * this).toInt()
