package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.feature.login.api.LoginValidationEntryActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
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

    @Test
    fun selecting_each_validation_entry_dismisses_sheet_and_invokes_only_its_action() {
        val invokedActions = mutableListOf<String>()
        setLoginContent(
            validationEntryActions = LoginValidationEntryActions(
                onOpenCameraValidation = { invokedActions += "camera" },
                onOpenBackupFaceVerification = { invokedActions += "backup_face" },
                onOpenManualFaceCapture = { invokedActions += "manual_face" },
                onOpenFaceVerificationValidation = { invokedActions += "face_verification" },
                onOpenNfcValidation = { invokedActions += "nfc" },
            ),
        )

        val expectedSelections =
            listOf(
                "login_face_verification_test_entry" to "face_verification",
                "login_nfc_test_entry" to "nfc",
                "login_camera_test_entry" to "camera",
                "login_legacy_face_test_entry" to "backup_face",
                "login_manual_face_test_entry" to "manual_face",
            )

        expectedSelections.forEachIndexed { index, (testTag, actionName) ->
            composeRule.onNodeWithTag("login_main_logo").performTouchInput {
                longClick()
            }
            composeRule.onNodeWithTag("login_test_entry_sheet").assertExists()

            composeRule.onNodeWithTag(testTag).performTouchInput {
                click()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("login_test_entry_sheet").assertDoesNotExist()
            assertEquals(expectedSelections.take(index + 1).map { it.second }, invokedActions)
            assertEquals(actionName, invokedActions.last())
        }
    }

    private fun setLoginContent(
        validationEntryActions: LoginValidationEntryActions = LoginValidationEntryActions(),
    ) {
        composeRule.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = { _, _ -> },
                        validationEntryActions = validationEntryActions,
                    ),
                    agreementLinks = LoginAgreementLinks(
                        userAgreementUrl = "https://example.test/user-agreement",
                        privacyPolicyUrl = "https://example.test/privacy-policy",
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
