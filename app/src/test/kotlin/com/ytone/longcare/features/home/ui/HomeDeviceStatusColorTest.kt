package com.ytone.longcare.features.home.ui

import androidx.compose.ui.graphics.Color
import com.ytone.longcare.features.maindashboard.ui.homeDeviceStatusColor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeDeviceStatusColorTest {
    @Test fun `server colors are preserved and malformed values use a neutral fallback`() {
        val fallback = Color.Gray
        assertEquals(Color(0xFF27AE60), homeDeviceStatusColor("#27AE60", fallback))
        assertEquals(Color(0x80E53935), homeDeviceStatusColor("#80E53935", fallback))
        assertEquals(fallback, homeDeviceStatusColor("", fallback))
        assertEquals(fallback, homeDeviceStatusColor("   ", fallback))
        assertEquals(fallback, homeDeviceStatusColor("invalid-color", fallback))
    }
}
