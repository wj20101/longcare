package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
                    onRequestRefocus = { refocusCount++ },
                    onClearResult = {},
                    onCopyResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_refocus_button").performClick()
        assertEquals(1, refocusCount)
    }

    @Test
    fun panel_is_read_only_and_copy_button_depends_on_uid_presence() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(
                        captureState = R65CHidCaptureState.LastCaptureSucceeded,
                        liveInputBuffer = "AB12",
                        lastRawInput = "AB12\n",
                        lastNormalizedUid = "AB12",
                        lastCompletedAt = "12:34:56",
                    ),
                    onRequestRefocus = {},
                    onClearResult = {},
                    onCopyResult = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("r65c_input_field").assertCountEquals(0)
        composeRule.onNodeWithTag("r65c_live_input_value").assertTextEquals("AB12")
        composeRule.onNodeWithTag("r65c_copy_button").assertIsEnabled()
        composeRule.onAllNodesWithTag("r65c_refocus_button").assertCountEquals(1)
        composeRule.onAllNodesWithTag("r65c_clear_button").assertCountEquals(1)
        composeRule.onAllNodesWithTag("r65c_copy_button").assertCountEquals(1)
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
                    onRequestRefocus = {},
                    onClearResult = {},
                    onCopyResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_live_input_value").assertTextEquals("AB")
        composeRule.onNodeWithTag("r65c_last_raw_value").assertTextEquals("CD\r")
        composeRule.onNodeWithTag("r65c_last_uid_value").assertTextEquals("CD")
        composeRule.onNodeWithTag("r65c_last_completed_at").assertTextEquals("12:34:56")
    }

    @Test
    fun copy_button_is_disabled_when_uid_absent() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(
                        captureState = R65CHidCaptureState.LastCaptureFailed("未解析出卡号"),
                        lastRawInput = "###",
                        lastNormalizedUid = null,
                    ),
                    onRequestRefocus = {},
                    onClearResult = {},
                    onCopyResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_copy_button").assertIsNotEnabled()
    }

    @Test
    fun body_shows_r65c_panel_and_hides_legacy_usb_host_actions() {
        composeRule.setContent {
            LongCareTheme {
                NfcTestBody(
                    enabled = true,
                    supportsNfc = false,
                    r65cPanelState = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReadyForScan,
                    ),
                    onR65CRequestRefocus = {},
                    onR65CClearResult = {},
                    onR65CCopyResult = {},
                )
            }
        }

        composeRule.onAllNodesWithText("R65C HID 键盘口测试").assertCountEquals(1)
        composeRule.onAllNodesWithText("刷新设备").assertCountEquals(0)
        composeRule.onAllNodesWithText("申请权限").assertCountEquals(0)
        composeRule.onAllNodesWithText("开始尝试读取").assertCountEquals(0)
    }
}
