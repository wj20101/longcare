package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.feature.photoupload.databinding.WatermarkViewBinding
import com.ytone.longcare.model.WatermarkData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraWatermarkOverlayTest {
    @Test fun salesWatermarkHidesInsuredPersonAndAddressOnly() {
        val binding = bindingFor(WatermarkData("长者顾问", "", "销售顾问", ""))
        assertEquals("长者顾问", binding.serviceTypeTextView.text.toString())
        assertEquals(View.GONE, binding.insuredPersonField.visibility)
        assertEquals(View.GONE, binding.addressRow.visibility)
        assertEquals(View.VISIBLE, binding.caregiverTextView.visibility)
        assertEquals(View.VISIBLE, binding.captureTimeTextView.visibility)
        assertEquals(View.VISIBLE, binding.coordinatesTextView.visibility)
    }

    @Test fun nursingWatermarkKeepsInsuredPersonAndAddress() {
        val binding = bindingFor(WatermarkData("服务前", "张三", "李四", "测试地址"))
        assertEquals(View.VISIBLE, binding.insuredPersonField.visibility)
        assertEquals(View.VISIBLE, binding.addressRow.visibility)
        assertEquals("测试地址", binding.captureLocationTextView.text.toString())
    }

    private fun bindingFor(data: WatermarkData): WatermarkViewBinding {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return WatermarkViewBinding.inflate(LayoutInflater.from(context), FrameLayout(context), false).also {
            bindCameraWatermark(it, data, "2026-08-06 10:00:00", "120.1,30.2")
        }
    }
}
