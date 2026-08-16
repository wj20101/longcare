package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
    fun long_pressing_logo_opens_all_validation_entries() {
        setLoginContent()

        composeRule.onNodeWithText("功能验证").assertDoesNotExist()

        composeRule.onNodeWithTag("login_main_logo").performTouchInput {
            longClick()
        }

        composeRule.onNodeWithText("功能验证").assertExists()
        composeRule.onNodeWithText("人脸验证").assertExists()
        composeRule.onNodeWithText("碰一碰 / R65C 验证").assertExists()
        composeRule.onNodeWithText("拍照验证").assertExists()
        composeRule.onNodeWithText("备用人脸验证").assertExists()
        composeRule.onNodeWithText("人脸采集验证").assertExists()
    }

    private fun setLoginContent() {
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
    }
}
