package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertHasNoClickAction
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginHiddenTestEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapping_logo_does_not_open_validation_entries() {
        setLoginContent()

        composeRule.onNodeWithTag("login_main_logo").performTouchInput {
            click()
        }

        composeRule.onNodeWithText("功能验证").assertDoesNotExist()
    }

    @Test
    fun long_pressing_logo_has_no_validation_action_and_login_still_submits() {
        var submitted: Pair<String, String>? = null
        setLoginContent(onLogin = { phone, code -> submitted = phone to code })

        composeRule.onNodeWithText("功能验证").assertDoesNotExist()

        composeRule.onNodeWithTag("login_main_logo").performTouchInput {
            longClick()
        }

        composeRule.onNodeWithTag("login_main_logo").assertHasNoClickAction()
        composeRule.onNodeWithText("功能验证").assertDoesNotExist()
        composeRule.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        composeRule.onNodeWithTag("login_verification_code_input").performTextInput("123456")
        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.runOnIdle { assertEquals("13800138000" to "123456", submitted) }
    }

    private fun setLoginContent(onLogin: (String, String) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = { _, _ -> }
                    ),
                    loginState = LoginUiState.Idle,
                    sendSmsState = SendSmsCodeUiState.Idle,
                    startConfigState = StartConfigUiState.Idle,
                    countdownSeconds = 0,
                    onSendCodeClick = {},
                    onLoginClick = onLogin
                )
            }
        }
    }
}
