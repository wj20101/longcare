package com.ytone.longcare.features.home.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeScreenPermissionPolicyTest {

    @Test
    fun `home screen does not auto request camera or location permissions on entry`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt"
        ).readText()

        assertFalse(source.contains("buildRequiredPermissions()"))
        assertFalse(source.contains("ActivityResultContracts.RequestMultiplePermissions"))
        assertFalse(source.contains("Manifest.permission.CAMERA"))
        assertFalse(source.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun `home screen does not auto show service compatibility guides on entry`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt"
        ).readText()

        assertFalse(source.contains("getRequiredPermissionGuide"))
        assertFalse(source.contains("getBatteryGuideStep"))
        assertFalse(source.contains("getPopupPermissionGuideMessage"))
    }
}
