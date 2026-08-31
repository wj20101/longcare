package com.ytone.longcare.features.maindashboard.utils

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcTestTagDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recognized_nfc_tag_renders_information_dialog_with_uid() {
        composeRule.setContent {
            LongCareTheme {
                RenderNfcTestTagDialog(
                    showDialog = true,
                    nfcTagId = "012A7F",
                    onCopy = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("检测到 NFC 标签").assertExists()
        composeRule.onNodeWithText("标签 ID：012A7F").assertExists()
        composeRule.onNodeWithText("复制").assertExists()
        composeRule.onNodeWithText("关闭").assertExists()
    }
}
