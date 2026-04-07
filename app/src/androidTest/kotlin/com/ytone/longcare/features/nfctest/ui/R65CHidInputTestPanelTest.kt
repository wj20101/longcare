package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R65CHidInputTestPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun refocus_button_invokes_callback() {
        var refocusCount = 0

        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(captureState = R65CHidCaptureState.WaitingForFocus),
                    onInputChanged = {},
                    onFocusChanged = {},
                    onRequestRefocus = { refocusCount++ },
                    onClearResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_refocus_button").performClick()
        assertEquals(1, refocusCount)
    }

    @Test
    fun input_field_requests_focus_on_token_change() {
        var panelState by mutableStateOf(
            R65CHidPanelState(captureState = R65CHidCaptureState.WaitingForFocus),
        )
        var externalValue by mutableStateOf("")

        composeRule.setContent {
            LongCareTheme {
                Column {
                    R65CHidInputTestPanel(
                        state = panelState,
                        onInputChanged = {},
                        onFocusChanged = {},
                        onRequestRefocus = {},
                        onClearResult = {},
                    )
                    OutlinedTextField(
                        value = externalValue,
                        onValueChange = { externalValue = it },
                        label = { Text("外部输入框") },
                        modifier = Modifier.testTag("external_input_field"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("r65c_input_field").assertIsFocused()
        composeRule.onNodeWithTag("external_input_field").performClick()
        composeRule.onNodeWithTag("r65c_input_field").assertIsNotFocused()

        composeRule.runOnUiThread {
            panelState = panelState.copy(focusRequestToken = panelState.focusRequestToken + 1)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("r65c_input_field").assertIsFocused()
    }

    @Test
    fun panel_shows_live_input_and_last_result_separately() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReceivingInput,
                        liveInputBuffer = "AB",
                        lastRawInput = "CD\r",
                        lastNormalizedUid = "CD",
                        lastCompletedAt = "12:34:56",
                    ),
                    onInputChanged = {},
                    onFocusChanged = {},
                    onRequestRefocus = {},
                    onClearResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_live_input_value").assertTextEquals("AB")
        composeRule.onNodeWithTag("r65c_last_raw_value").assertTextEquals("CD\r")
        composeRule.onNodeWithTag("r65c_last_uid_value").assertTextEquals("CD")
        composeRule.onNodeWithTag("r65c_last_completed_at").assertTextEquals("12:34:56")
    }
}
