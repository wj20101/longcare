package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.debug.NfcTestEntrySession
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginHiddenTestEntryToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        NfcTestEntrySession.resetForTest()
    }

    @Test
    fun test_buttons_are_hidden_by_default_and_revealed_after_logo_long_press() {
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
                    onLoginClick = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("碰一碰测试").assertDoesNotExist()

        composeRule.onNodeWithTag("login_main_logo").performTouchInput {
            longClick()
        }

        composeRule.onNodeWithText("碰一碰测试").assertExists()
    }
}
