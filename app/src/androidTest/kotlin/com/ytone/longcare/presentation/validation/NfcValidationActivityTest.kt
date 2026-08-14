package com.ytone.longcare.presentation.validation

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.common.utils.NfcUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcValidationActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<NfcValidationActivity>()

    @Test
    fun validation_activity_shows_exactly_one_device_mode() {
        composeRule.onNodeWithText("碰一碰验证").assertExists()

        if (NfcUtils.isNfcSupported(composeRule.activity)) {
            composeRule.onNodeWithText("手机 NFC 读卡").assertExists()
            composeRule.onNodeWithText("R65C 外接读卡器").assertDoesNotExist()
        } else {
            composeRule.onNodeWithText("R65C 外接读卡器").assertExists()
            composeRule.onNodeWithText("手机 NFC 读卡").assertDoesNotExist()
        }
    }
}
