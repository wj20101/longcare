package com.ytone.longcare.features.sales

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesSubmitSuccessScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmAndReturn_leavesTheResultPage() {
        composeRule.setContent {
            var showResult by remember { mutableStateOf(true) }
            SalesPageBackground {
                if (showResult) {
                    SalesSubmitSuccessScreen(
                        onBack = { showResult = false },
                        onEvaluation = {},
                    )
                } else {
                    Text("已返回首页")
                }
            }
        }

        composeRule.onNodeWithText("确认并返回").performClick()

        composeRule.onNodeWithText("已返回首页").assertIsDisplayed()
        composeRule.onNodeWithText("信息提交结果").assertDoesNotExist()
    }

    @Test
    fun topBack_leavesTheResultPage() {
        composeRule.setContent {
            var showResult by remember { mutableStateOf(true) }
            SalesPageBackground {
                if (showResult) {
                    SalesSubmitSuccessScreen(
                        onBack = { showResult = false },
                        onEvaluation = {},
                    )
                } else {
                    Text("已返回首页")
                }
            }
        }

        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithText("已返回首页").assertIsDisplayed()
        composeRule.onNodeWithText("信息提交结果").assertDoesNotExist()
    }
}
