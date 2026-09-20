package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test

class LoginLogoIsolationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logo_without_entry_callback_is_disabled() {
        compose.setContent { LongCareTheme { Box { LoginBrandingHeader() } } }
        val logo = compose.onNodeWithTag("login_main_logo")
        logo.assertIsNotEnabled()
        logo.performTouchInput { click(); longClick() }
        logo.assertExists().assertIsNotEnabled()
        compose.onNodeWithText("功能验证").assertDoesNotExist()
    }
}
