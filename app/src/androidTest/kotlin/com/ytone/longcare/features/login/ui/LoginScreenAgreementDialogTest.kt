package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.R
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenAgreementDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val agreementConfirmMessage = context.getString(R.string.login_agreement_confirm_message)
    private val agreementCancelAction = context.getString(R.string.login_agreement_cancel_action)
    private val agreementConfirmAction = context.getString(R.string.login_agreement_confirm_action)

    @Test
    fun login_without_agreement_shows_confirmation_dialog_and_does_not_submit() {
        var loginCount = 0

        setLoginScreenContent(onLoginInvoked = { loginCount++ })

        fillLoginForm()
        composeRule.onNodeWithTag("login_submit_button").performClick()

        composeRule.onNodeWithText(agreementConfirmMessage).assertExists()
        assertEquals(0, loginCount)
    }

    @Test
    fun cancel_from_confirmation_dialog_dismisses_dialog_without_submitting() {
        var loginCount = 0

        setLoginScreenContent(onLoginInvoked = { loginCount++ })

        fillLoginForm()
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.onNodeWithText(agreementCancelAction).performClick()

        composeRule.onNodeWithText(agreementConfirmMessage).assertDoesNotExist()
        assertEquals(0, loginCount)
    }

    @Test
    fun confirm_and_agree_checks_checkbox_and_submits_login() {
        var loginCount = 0

        setLoginScreenContent(onLoginInvoked = { loginCount++ })

        fillLoginForm()
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.onNodeWithText(agreementConfirmAction).performClick()

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
        assertEquals(1, loginCount)
    }

    private fun setLoginScreenContent(onLoginInvoked: () -> Unit) {
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
                    onLoginClick = { _, _ -> onLoginInvoked() }
                )
            }
        }
    }

    private fun fillLoginForm() {
        composeRule.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        composeRule.onNodeWithTag("login_verification_code_input").performTextInput("123456")
    }
}
