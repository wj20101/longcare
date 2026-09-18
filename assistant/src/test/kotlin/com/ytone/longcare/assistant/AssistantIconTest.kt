package com.ytone.longcare.assistant

import android.app.Application
import android.util.DisplayMetrics
import android.util.TypedValue
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AssistantIconTest {
    @Test @Config(sdk = [35])
    fun brandBitmapUsesXxhdpiDensity() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val value = TypedValue()
        context.resources.getValue(R.drawable.assistant_brand, value, true)
        assertEquals(DisplayMetrics.DENSITY_XXHIGH, value.density)
    }

    @Test @Config(sdk = [24])
    fun preAdaptiveDevicesHaveRasterFallback() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(context.getDrawable(R.mipmap.ic_assistant) is BitmapDrawable)
    }

    @Test @Config(sdk = [26])
    fun adaptiveDevicesLoadBothLayers() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icon = context.getDrawable(R.mipmap.ic_assistant) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
    }

    @Test @Config(sdk = [35])
    fun themedDevicesHaveDedicatedMonochromeLayer() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icon = context.getDrawable(R.mipmap.ic_assistant) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
    }
}
