package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateKind
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateValue
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidCompletionReason
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R65CHidRawValidationPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun start_listening_button_invokes_callback() {
        var startCount = 0

        composeRule.setContent {
            LongCareTheme {
                R65CHidRawValidationPanel(
                    state = R65CHidRawValidationState(
                        captureState = R65CHidRawCaptureState.Idle,
                        isListening = false,
                    ),
                    onTextFieldValueChanged = {},
                    onFocusChanged = {},
                    onStartListening = { startCount++ },
                    onStopListening = {},
                    onRequestRefocus = {},
                    onClearSession = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_raw_start_button").performClick()

        assertEquals(1, startCount)
    }

    @Test
    fun stop_listening_button_invokes_callback() {
        var stopCount = 0

        composeRule.setContent {
            LongCareTheme {
                R65CHidRawValidationPanel(
                    state = R65CHidRawValidationState(
                        captureState = R65CHidRawCaptureState.Armed,
                        isListening = true,
                    ),
                    onTextFieldValueChanged = {},
                    onFocusChanged = {},
                    onStartListening = {},
                    onStopListening = { stopCount++ },
                    onRequestRefocus = {},
                    onClearSession = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_raw_stop_button").performClick()

        assertEquals(1, stopCount)
    }

    @Test
    fun clear_session_button_invokes_callback() {
        var clearCount = 0

        composeRule.setContent {
            LongCareTheme {
                R65CHidRawValidationPanel(
                    state = R65CHidRawValidationState(
                        captureState = R65CHidRawCaptureState.Completed,
                    ),
                    onTextFieldValueChanged = {},
                    onFocusChanged = {},
                    onStartListening = {},
                    onStopListening = {},
                    onRequestRefocus = {},
                    onClearSession = { clearCount++ },
                )
            }
        }

        composeRule.onNodeWithTag("r65c_raw_clear_button").performClick()

        assertEquals(1, clearCount)
    }

    @Test
    fun panel_shows_text_events_and_candidates() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidRawValidationPanel(
                    state = R65CHidRawValidationState(
                        captureState = R65CHidRawCaptureState.Completed,
                        textFieldValue = "901948不EA8想0想",
                        lastSessionTextFieldValue = "901948不EA8想0想",
                        lastSessionAssembledChars = "901948EA80",
                        lastCompletedReason = R65CHidCompletionReason.EnterKey,
                        candidateValues = listOf(
                            R65CHidCandidateValue(
                                kind = R65CHidCandidateKind.HexFiltered,
                                value = "901948EA80",
                                note = "looks like 10 hex",
                            ),
                        ),
                        lastCompletedAt = "21:52:05",
                    ),
                    onTextFieldValueChanged = {},
                    onFocusChanged = {},
                    onStartListening = {},
                    onStopListening = {},
                    onRequestRefocus = {},
                    onClearSession = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_raw_text_value").assertTextEquals("901948不EA8想0想")
        composeRule.onNodeWithTag("r65c_raw_assembled_value").assertTextEquals("901948EA80")
        composeRule.onNodeWithTag("r65c_candidate_0_value").assertTextEquals("901948EA80")
        composeRule.onNodeWithTag("r65c_completed_reason").assertTextEquals("Enter结束")
        composeRule.onNodeWithTag("r65c_completed_at").assertTextEquals("21:52:05")
    }

}
