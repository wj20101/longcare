package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test

class LoginAgreementConsentRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkbox_toggles_only_when_checkbox_itself_is_tapped() {
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            LongCareTheme {
                AgreementConsentSection(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    onUserAgreementClick = {},
                    onPrivacyPolicyClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()

        composeRule.onNodeWithText("登录即表明已阅读并同意", substring = true).performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
    }
}
