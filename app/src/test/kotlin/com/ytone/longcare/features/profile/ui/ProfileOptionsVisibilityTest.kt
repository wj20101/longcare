package com.ytone.longcare.features.profile.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileOptionsVisibilityTest {

    @Test
    fun `unimplemented profile options are hidden`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt"
        ).readText()
        val screenSource = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreen.kt"
        ).readText()

        assertFalse(source.contains("信息上报"))
        assertFalse(source.contains("个人信息"))
        assertFalse(source.contains("设置"))
        assertFalse(source.contains("onClick = {}"))
        assertFalse(screenSource.contains("OptionsCard()"))
    }
}
