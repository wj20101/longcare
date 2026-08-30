package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
                    onPrivacyPolicyClick = {},
                    modifier = Modifier.testTag("login_agreement_row")
                )
            }
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()

        composeRule.onNodeWithTag("login_agreement_prefix_text").performTouchInput {
            click(center)
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
    }
}
