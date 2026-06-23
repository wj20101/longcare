package com.ytone.longcare.features.profile.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOptionsVisibilityTest {

    @Test
    fun `unimplemented profile options are hidden`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt"
        ).readText()
        val optionTitles = Regex("""text\s*=\s*"([^"]+)"""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(optionTitles.contains("用户协议"))
        assertTrue(optionTitles.contains("隐私政策"))
        assertFalse(optionTitles.contains("信息上报"))
        assertFalse(optionTitles.contains("个人信息"))
        assertFalse(optionTitles.contains("设置"))
        assertFalse(source.contains("onClick = {}"))
    }
}
