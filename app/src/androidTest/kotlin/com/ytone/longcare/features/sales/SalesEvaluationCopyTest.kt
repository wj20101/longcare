package com.ytone.longcare.features.sales

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesEvaluationCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disconnectedGuideDoesNotClaimThatTheDeviceIsConnected() {
        composeRule.setContent {
            SalesPageBackground {
                SalesEvaluationGuideScreen(
                    connectedDeviceName = null,
                    progressText = "",
                    onBack = {},
                    onOpenSdk = {},
                )
            }
        }

        composeRule.onNodeWithText("设备未连接").assertExists()
        composeRule.onNodeWithText("设备已连接").assertDoesNotExist()
        composeRule
            .onNodeWithText("请按照图示握持设备，再进入评估页面连接设备")
            .assertExists()
        composeRule.onNodeWithText("连接设备并开始评估").assertExists()
    }

    @Test
    fun evaluationChoicesUseFormalCustomerFacingCopy() {
        composeRule.setContent {
            SalesPageBackground {
                SalesEvaluationChoiceScreen(
                    onBack = {},
                    onAutomaticEvaluation = {},
                    onFormEvaluation = {},
                )
            }
        }

        composeRule.onNodeWithText("手握设备即可完成评估").assertExists()
        composeRule.onNodeWithText("通过问卷完成评估").assertExists()
        composeRule.onNodeWithText("手握住设备即可评估完成").assertDoesNotExist()
        composeRule.onNodeWithText("问卷调研形式评估").assertDoesNotExist()
    }
}
