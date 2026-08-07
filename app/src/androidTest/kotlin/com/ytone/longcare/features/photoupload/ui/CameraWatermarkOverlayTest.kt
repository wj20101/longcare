package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.databinding.WatermarkViewBinding
import com.ytone.longcare.model.WatermarkData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraWatermarkOverlayTest {

    @Test
    fun salesWatermarkHidesInsuredPersonAndCaptureAddressRows() {
        val binding = createBinding()

        bindCameraWatermark(
            binding = binding,
            watermarkData =
                WatermarkData(
                    title = "长者顾问",
                    insuredPerson = "",
                    caregiver = "销售顾问",
                    address = "",
                ),
            time = "2026-08-06 10:00:00",
            location = "120.1,30.2",
            logoImg = "",
        )

        assertEquals("长者顾问", binding.serviceTypeTextView.text.toString())
        assertEquals(View.GONE, binding.insuredPersonLabelTextView.visibility)
        assertEquals(View.GONE, binding.insuredPersonTextView.visibility)
        assertEquals(View.GONE, binding.captureLocationLabelTextView.visibility)
        assertEquals(View.GONE, binding.captureLocationTextView.visibility)
        assertEquals(View.VISIBLE, binding.caregiverLabelTextView.visibility)
        assertEquals(View.VISIBLE, binding.captureTimeLabelTextView.visibility)
        assertEquals(View.VISIBLE, binding.coordinatesLabelTextView.visibility)
        assertEquals(
            binding.captureTimeLabelTextView.id,
            binding.coordinatesLabelTextView.layoutParams
                .let { it as ConstraintLayout.LayoutParams }
                .topToBottom,
        )
    }

    @Test
    fun careWatermarkKeepsExistingInsuredPersonAndAddressRows() {
        val binding = createBinding()

        bindCameraWatermark(
            binding = binding,
            watermarkData =
                WatermarkData(
                    title = "服务前",
                    insuredPerson = "张三",
                    caregiver = "李四",
                    address = "测试地址",
                ),
            time = "2026-08-06 10:00:00",
            location = "120.1,30.2",
            logoImg = "",
        )

        assertEquals(View.VISIBLE, binding.insuredPersonLabelTextView.visibility)
        assertEquals(View.VISIBLE, binding.insuredPersonTextView.visibility)
        assertEquals(View.VISIBLE, binding.captureLocationLabelTextView.visibility)
        assertEquals(View.VISIBLE, binding.captureLocationTextView.visibility)
        assertEquals(
            binding.captureLocationLabelTextView.id,
            binding.coordinatesLabelTextView.layoutParams
                .let { it as ConstraintLayout.LayoutParams }
                .topToBottom,
        )
    }

    private fun createBinding(): WatermarkViewBinding {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return WatermarkViewBinding.inflate(
            LayoutInflater.from(context),
            FrameLayout(context),
            false,
        )
    }
}
