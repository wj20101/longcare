package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.feature.login.R
import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.model.StartConfigResultModel
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
    private val sendCodeAction = context.getString(R.string.login_send_code_button_text)
    private val userAgreementAction = context.getString(R.string.login_user_agreement)
    private val privacyPolicyAction = context.getString(R.string.login_privacy_policy)
    private val fallbackLinks = LoginAgreementLinks(
        userAgreementUrl = "https://fallback.example.test/user-agreement",
        privacyPolicyUrl = "https://fallback.example.test/privacy-policy",
    )

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
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        assertEquals(0, loginCount)
    }

    @Test
    fun agree_from_confirmation_dialog_checks_checkbox_without_submitting_login() {
        var loginCount = 0

        setLoginScreenContent(onLoginInvoked = { loginCount++ })

        fillLoginForm()
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.onNodeWithText(agreementConfirmAction).performClick()

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
        assertEquals(0, loginCount)
    }

    @Test
    fun send_code_without_agreement_shows_confirmation_dialog_and_does_not_submit() {
        var sendCodeCount = 0

        setLoginScreenContent(onSendCodeInvoked = { sendCodeCount++ })
        composeRule.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        composeRule.onNodeWithText(sendCodeAction).performClick()

        composeRule.onNodeWithText(agreementConfirmMessage).assertExists()
        assertEquals(0, sendCodeCount)
    }

    @Test
    fun successful_send_code_state_requests_verification_code_focus() {
        setLoginScreenContent(
            sendSmsState = SendSmsCodeUiState.Success,
            initialAgreementChecked = true,
        )

        composeRule.onNodeWithTag("login_verification_code_input").assertIsFocused()
    }

    @Test
    fun dynamic_user_agreement_url_is_forwarded_without_host_filtering() {
        val dynamicUrl = "http://192.0.2.1:8080/user-agreement?source=server"
        val openedUrls = mutableListOf<String>()
        setLoginScreenContent(
            startConfigState = StartConfigUiState.Success(
                StartConfigResultModel(userXieYiUrl = dynamicUrl),
            ),
            onOpenWebPage = { url, _ -> openedUrls += url },
        )

        composeRule.onNodeWithText(userAgreementAction, useUnmergedTree = true).performClick()

        assertEquals(listOf(dynamicUrl), openedUrls)
    }

    @Test
    fun unavailable_user_agreement_uses_app_fallback_url() {
        val openedUrls = mutableListOf<String>()
        setLoginScreenContent(
            startConfigState = StartConfigUiState.Loading,
            onOpenWebPage = { url, _ -> openedUrls += url },
        )

        composeRule.onNodeWithText(userAgreementAction, useUnmergedTree = true).performClick()

        assertEquals(listOf(fallbackLinks.userAgreementUrl), openedUrls)
    }

    @Test
    fun empty_dynamic_user_agreement_uses_app_fallback_url() {
        val openedUrls = mutableListOf<String>()
        setLoginScreenContent(
            startConfigState = StartConfigUiState.Success(
                StartConfigResultModel(userXieYiUrl = ""),
            ),
            onOpenWebPage = { url, _ -> openedUrls += url },
        )

        composeRule.onNodeWithText(userAgreementAction, useUnmergedTree = true).performClick()

        assertEquals(listOf(fallbackLinks.userAgreementUrl), openedUrls)
    }

    @Test
    fun privacy_policy_uses_app_fallback_url() {
        val openedUrls = mutableListOf<String>()
        setLoginScreenContent(onOpenWebPage = { url, _ -> openedUrls += url })

        composeRule.onNodeWithText(privacyPolicyAction, useUnmergedTree = true).performClick()

        assertEquals(listOf(fallbackLinks.privacyPolicyUrl), openedUrls)
    }

    @Test
    fun agreement_selection_survives_saved_state_restoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = { _, _ -> },
                    ),
                    agreementLinks = fallbackLinks,
                    loginState = LoginUiState.Idle,
                    sendSmsState = SendSmsCodeUiState.Idle,
                    startConfigState = StartConfigUiState.Idle,
                    countdownSeconds = 0,
                    onSendCodeClick = {},
                    onLoginClick = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
    }

    private fun setLoginScreenContent(
        onLoginInvoked: () -> Unit = {},
        onSendCodeInvoked: () -> Unit = {},
        onOpenWebPage: (String, String) -> Unit = { _, _ -> },
        sendSmsState: SendSmsCodeUiState = SendSmsCodeUiState.Idle,
        startConfigState: StartConfigUiState = StartConfigUiState.Idle,
        initialAgreementChecked: Boolean = false,
    ) {
        composeRule.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = onOpenWebPage,
                    ),
                    agreementLinks = fallbackLinks,
                    loginState = LoginUiState.Idle,
                    sendSmsState = sendSmsState,
                    startConfigState = startConfigState,
                    countdownSeconds = 0,
                    initialAgreementChecked = initialAgreementChecked,
                    onSendCodeClick = { onSendCodeInvoked() },
                    onLoginClick = { _, _ -> onLoginInvoked() },
                )
            }
        }
    }

    private fun fillLoginForm() {
        composeRule.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        composeRule.onNodeWithTag("login_verification_code_input").performTextInput("123456")
    }
}
