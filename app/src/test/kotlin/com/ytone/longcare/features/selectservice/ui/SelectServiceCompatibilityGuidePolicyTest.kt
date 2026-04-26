package com.ytone.longcare.features.selectservice.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectServiceCompatibilityGuidePolicyTest {

    @Test
    fun `select service next step checks service compatibility guides before countdown`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreen.kt"
        ).readText()

        assertTrue(source.contains("getRequiredPermissionGuide"))
        assertTrue(source.contains("getBatteryGuideStep"))
        assertTrue(source.contains("getPopupPermissionGuideMessage"))
        assertTrue(source.contains("onNextStep = singleClick"))
        assertTrue(source.contains("showCompatibilityGuideIfNeeded"))
        assertTrue(source.contains("onNavigateToServiceCountdown"))
    }
}
