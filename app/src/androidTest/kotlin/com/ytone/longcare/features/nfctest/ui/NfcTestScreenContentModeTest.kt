package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcTestScreenContentModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun supports_nfc_shows_only_nfc_content() {
        composeRule.setContent {
            LongCareTheme {
                NfcTestBody(
                    enabled = true,
                    supportsNfc = true,
                    r65cPanelState = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReadyForScan,
                    ),
                    onR65CRequestRefocus = {},
                    onR65CClearResult = {},
                    onR65CCopyResult = {},
                )
            }
        }

        composeRule.onAllNodesWithText("碰一碰ID读取").assertCountEquals(1)
        composeRule.onAllNodesWithText("R65C HID 键盘口测试").assertCountEquals(0)
        composeRule.onAllNodesWithText("原始 HID 输出验证").assertCountEquals(0)
    }

    @Test
    fun no_nfc_support_shows_only_hid_content() {
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
        composeRule.onAllNodesWithText("碰一碰ID读取").assertCountEquals(0)
        composeRule.onAllNodesWithText("原始 HID 输出验证").assertCountEquals(0)
    }
}
