package com.ytone.longcare.features.selectservice.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectServiceCompatibilityGuidePolicyTest {

    @Test
    fun `background settings UI is shared without an app or feature dependency`() {
        val sharedDialog = File(
            "../core/ui/src/main/kotlin/com/ytone/longcare/core/ui/permission/BackgroundSettingsDialog.kt"
        )
        assertTrue(sharedDialog.isFile)
        val source = sharedDialog.readText()
        assertFalse(source.contains("com.ytone.longcare.R"))
        assertFalse(source.contains("com.ytone.longcare.features."))
        assertFalse(File(
            "src/main/kotlin/com/ytone/longcare/features/selectservice/ui/UnifiedPermissionGuideDialog.kt"
        ).exists())
    }
}
