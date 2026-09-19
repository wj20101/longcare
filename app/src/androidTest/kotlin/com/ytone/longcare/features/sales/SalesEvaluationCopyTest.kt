package com.ytone.longcare.features.sales

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.integration.qlz.QlzDeviceOption
import com.ytone.longcare.integration.qlz.QlzEvaluationIssue
import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUiState
import com.ytone.longcare.integration.qlz.QlzFingerContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesEvaluationCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun completionUsesBackendGradeAndEmptyGradeCanRefresh() {
        val grade = androidx.compose.runtime.mutableStateOf<String?>(null)
        var refreshes = 0
        composeRule.setContent {
            SalesPageBackground {
                SalesEvaluationCompleteScreen(false, {}, {}, {},
                    grade = grade.value, onRefresh = { refreshes++ })
            }
        }
        composeRule.onNodeWithText("评估成功").assertExists()
        composeRule.onNodeWithText("评估等级待同步").assertExists()
        composeRule.onNodeWithText("刷新评估结果").performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes); grade.value = "A级" }
        composeRule.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        composeRule.onNodeWithText("A级级", substring = true).assertDoesNotExist()
        composeRule.runOnIdle { grade.value = "重度失能" }
        composeRule.onNodeWithText("评估成功，评估等级为：重度失能").assertExists()
    }

    @Test
    fun scanResultsExposeMaskedDevicesAndSelection() {
        val selected = mutableListOf<QlzDeviceOption>()
        val device =
            QlzDeviceOption(
                id = "qlz-device-1",
                displayName = "BM-S 100",
                maskedIdentifier = "••:••:••:••:AA:BB",
            )
        composeRule.setContent {
            SalesPageBackground {
                SalesDeviceStatusScreen(
                    evaluationState =
                        QlzEvaluationUiState(
                            stage = QlzEvaluationStage.SCAN_RESULTS,
                            devices = listOf(device),
                        ),
                    tokenReady = true,
                    onBack = {},
                    onStartScan = {},
                    onSelectDevice = selected::add,
                    onRetry = {},
                    onRecheckEnvironment = {},
                )
            }
        }

        composeRule.onNodeWithText("BM-S 100").assertExists()
        composeRule.onNodeWithText("••:••:••:••:AA:BB").assertExists()
        composeRule.onNodeWithTag("qlz_device_qlz-device-1").performClick()
        composeRule.runOnIdle { assertEquals(listOf(device), selected) }
    }

    @Test
    fun scanningDisablesRepeatedStart() {
        composeRule.setContent {
            SalesPageBackground {
                SalesDeviceStatusScreen(
                    evaluationState =
                        QlzEvaluationUiState(stage = QlzEvaluationStage.SCANNING),
                    tokenReady = true,
                    onBack = {},
                    onStartScan = {},
                    onSelectDevice = {},
                    onRetry = {},
                    onRecheckEnvironment = {},
                )
            }
        }

        composeRule.onNodeWithTag("qlz_scan_action").assertIsNotEnabled()
        composeRule.onNodeWithTag("qlz_scan_action").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Text,
                listOf(androidx.compose.ui.text.AnnotatedString("正在搜索附近的评估设备…")),
            )
        )
    }

    @Test
    fun guideShowsFiveFingerStateAndSafeUploadRetry() {
        var retried = false
        composeRule.setContent {
            SalesPageBackground {
                SalesEvaluationGuideScreen(
                    evaluationState =
                        QlzEvaluationUiState(
                            stage = QlzEvaluationStage.ERROR,
                            fingerContacts =
                                QlzFingerContacts(
                                    little = true,
                                    middle = true,
                                ),
                            issue = QlzEvaluationIssue.UPLOAD_FAILED,
                            recoveryAction = QlzEvaluationRecoveryAction.RETRY_UPLOAD,
                        ),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule
            .onNodeWithTag("qlz_finger_0")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "接触良好",
                )
            )
        composeRule
            .onNodeWithTag("qlz_finger_1")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "尚未接触",
                )
            )
        repeat(5) { index ->
            composeRule.onNodeWithTag("qlz_finger_$index").assertExists()
        }
        composeRule.onNodeWithText("检测结果上传失败，可重试上传本次结果").assertExists()
        composeRule.onNodeWithTag("qlz_retry_action").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
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
