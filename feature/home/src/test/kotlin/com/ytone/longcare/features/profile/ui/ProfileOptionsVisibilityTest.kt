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
        assertTrue(source.contains("R.string.profile_user_agreement"))
        assertTrue(source.contains("R.string.profile_privacy_policy"))
        assertFalse(source.contains("profile_information_reporting"))
        assertFalse(source.contains("profile_personal_information"))
        assertFalse(source.contains("profile_settings"))
        assertFalse(source.contains("onClick = {}"))
    }

    @Test
    fun `profile keeps version logout and explicit policy actions`() {
        val screen = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreen.kt"
        ).readText()
        val options = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt"
        ).readText()

        assertTrue(screen.contains("config.versionName"))
        assertTrue(screen.contains("config.versionCode"))
        assertFalse(screen.contains("BuildConfig"))
        assertTrue(screen.contains("viewModel.logout()"))
        assertTrue(options.contains("actions.onOpenUserAgreement"))
        assertTrue(options.contains("actions.onOpenPrivacyPolicy"))
    }
}
