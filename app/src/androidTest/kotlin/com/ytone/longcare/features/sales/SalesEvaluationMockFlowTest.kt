package com.ytone.longcare.features.sales

import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.integration.qlz.QlzDeviceOption
import com.ytone.longcare.integration.qlz.QlzEvaluationDriver
import com.ytone.longcare.integration.qlz.QlzEvaluationDriverEvent
import com.ytone.longcare.integration.qlz.QlzEvaluationDriverFactory
import com.ytone.longcare.integration.qlz.QlzEvaluationIssue
import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationSession
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUploadContext
import com.ytone.longcare.integration.qlz.QlzFingerContacts
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.platform.sales.SalesEvaluationFormEffect
import com.ytone.longcare.platform.sales.SalesEvaluationFormRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real session + real screens; only vendor I/O is fake. No login, network, or BLE access. */
@RunWith(AndroidJUnit4::class)
class SalesEvaluationMockFlowTest {
    @get:Rule val composeRule = createComposeRule()

    private val driver = ScriptedDriver()
    private val events = mutableListOf<QlzSdkEvent>()
    private val visible = mutableStateOf(true)
    private val formOpened = mutableStateOf(false)
    private val lastClickByTag = mutableMapOf<String, Long>()
    private var releases = 0
    private val session = QlzEvaluationSession(
        driverFactory = QlzEvaluationDriverFactory { driver },
        uploadContext = QlzEvaluationUploadContext(),
        onEvent = events::add,
        releaseLease = { releases++ },
    )

    @After fun closeSession() = session.close()

    @Test fun measurementContactsAndUploadRetryReachCompletionOnce() {
        showFlow()
        startMeasurement()
        repeat(5) { activeIndex ->
            emit(QlzEvaluationDriverEvent.FingerContactsChanged(
                QlzFingerContacts.from(BooleanArray(5) { it == activeIndex })
            ))
            repeat(5) { index ->
                composeRule.onNodeWithTag("qlz_finger_$index").assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        if (index == activeIndex) "接触良好" else "尚未接触",
                    )
                )
            }
        }
        emit(QlzEvaluationDriverEvent.ProgressChanged(3, 5))
        composeRule.runOnIdle { assertEquals(0.6f, session.state.value.progressFraction) }
        emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        composeRule.onNodeWithTag("qlz_retry_action").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, driver.uploads) }

        emit(QlzEvaluationDriverEvent.UploadFailed(500))
        composeRule.onNodeWithText("检测结果上传失败，可重试上传本次结果").assertExists()
        click("qlz_retry_action")
        composeRule.onNodeWithTag("qlz_retry_action").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, driver.retries) }
        emit(QlzEvaluationDriverEvent.UploadSucceeded("mock-record", "https://vendor.invalid/report", "80"))
        emit(QlzEvaluationDriverEvent.UploadSucceeded("duplicate", "https://vendor.invalid/report", "80"))
        composeRule.onNodeWithText("Mock H5 评估页").assertExists()
        composeRule.onNodeWithText("评估成功").assertDoesNotExist()
        // No business report was supplied to this screen harness.
        composeRule.onNodeWithText("查看评估报告").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, events.filterIsInstance<QlzSdkEvent.Completed>().size)
            assertEquals("", events.filterIsInstance<QlzSdkEvent.Completed>().single().reportUrl)
        }
    }

    @Test fun emptyScanCanRestartAndBackgroundStopsScanning() {
        showFlow()
        beginScan()
        emit(QlzEvaluationDriverEvent.ScanStopped)
        composeRule.onNodeWithText("暂未发现设备，请确认设备已开机并靠近手机").assertExists()
        click("qlz_scan_action")
        composeRule.onNodeWithTag("qlz_scan_action").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(2, driver.scans)
            val stops = driver.stops
            session.onHostStopped()
            session.startScan()
            assertEquals(stops + 1, driver.stops)
            assertEquals(2, driver.scans)
            session.onHostStarted()
        }
        click("qlz_scan_action")
        composeRule.runOnIdle { assertEquals(3, driver.scans) }
    }

    @Test fun disconnectOffersWorkingReconnect() = verifyReconnect(
        QlzEvaluationIssue.CONNECTION_LOST, "设备连接已断开，请靠近设备后重试"
    )

    @Test fun weakSignalOffersWorkingReconnect() = verifyReconnect(
        QlzEvaluationIssue.WEAK_SIGNAL, "设备信号较弱，请靠近设备后重试"
    )

    @Test fun lowPowerOffersWorkingReconnect() = verifyReconnect(
        QlzEvaluationIssue.LOW_POWER, "设备电量过低，请充电后重新连接"
    )

    @Test fun chargingPausesAndUnpluggingResumesMeasurement() {
        showFlow()
        startMeasurement()
        emit(QlzEvaluationDriverEvent.PowerChanged(true))
        composeRule.onNodeWithText("设备正在充电，检测已自动暂停；请拔下充电线后继续").assertExists()
        composeRule.runOnIdle { assertEquals(QlzEvaluationStage.POWER_PAUSED, session.state.value.stage) }
        emit(QlzEvaluationDriverEvent.PowerChanged(false))
        composeRule.onNodeWithText("设备正在充电，检测已自动暂停；请拔下充电线后继续").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(QlzEvaluationStage.MEASURING, session.state.value.stage) }
    }

    @Test fun paymentOnlyAllowsExitAndLateCompletionCannotUpload() {
        showFlow()
        startMeasurement()
        emit(QlzEvaluationDriverEvent.PaymentRequired)
        click("qlz_retry_action")
        composeRule.onNodeWithText("Mock 验证已退出").assertExists()
        emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        composeRule.runOnIdle {
            assertEquals(0, driver.uploads)
            assertEquals(1, driver.closes)
            assertEquals(1, releases)
        }
    }

    @Test fun leavingCompositionReleasesSessionAndIgnoresLateCallbacks() {
        showFlow()
        startMeasurement()
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        emit(QlzEvaluationDriverEvent.UploadSucceeded("late", "", ""))
        composeRule.runOnIdle {
            assertEquals(QlzEvaluationStage.CLOSED, session.state.value.stage)
            assertEquals(1, releases)
            assertEquals(1, driver.closes)
            assertEquals(0, driver.uploads)
            assertTrue(events.none { it is QlzSdkEvent.Completed })
        }
    }

    private fun verifyReconnect(issue: QlzEvaluationIssue, message: String) {
        showFlow()
        startMeasurement()
        emit(QlzEvaluationDriverEvent.Failed(0, issue, QlzEvaluationRecoveryAction.RETRY_CONNECTION))
        composeRule.onNodeWithText(message).assertExists()
        click("qlz_retry_action")
        composeRule.runOnIdle {
            assertEquals(1, driver.reconnections)
            assertEquals(QlzEvaluationStage.CONNECTING, session.state.value.stage)
        }
        emit(QlzEvaluationDriverEvent.CheckStarted)
        composeRule.onNodeWithText(message).assertDoesNotExist()
    }

    private fun showFlow() {
        composeRule.setContent {
            if (visible.value) {
                val state by session.state.collectAsState()
                DisposableEffect(session) { onDispose { session.close() } }
                val exit = { session.cancel(); visible.value = false }
                SalesPageBackground {
                    when {
                        formOpened.value -> Text("Mock H5 评估页")
                        state.stage == QlzEvaluationStage.COMPLETED -> SalesEvaluationFormEffect(
                            request = SalesEvaluationFormRequest(7, "mock-record", "https://internal.test/form"),
                            onLeaveDevice = session::close,
                            onOpenForm = { formOpened.value = true },
                            onConsumed = {},
                        )
                        state.selectedDevice != null -> SalesEvaluationGuideScreen(
                            evaluationState = state,
                            onBack = exit,
                            onRetry = {
                                if (state.recoveryAction == QlzEvaluationRecoveryAction.RETRY_UPLOAD) {
                                    session.retryUpload()
                                } else {
                                    session.retryConnection()
                                }
                            },
                        )
                        else -> SalesDeviceStatusScreen(
                            evaluationState = state, tokenReady = true, onBack = exit,
                            onStartScan = { session.start("mock-token") },
                            onSelectDevice = { session.selectDevice(it.id) },
                            onRetry = session::startScan, onRecheckEnvironment = {},
                        )
                    }
                }
            } else {
                Text("Mock 验证已退出")
            }
        }
    }

    private fun beginScan() {
        click("qlz_scan_action")
        emit(QlzEvaluationDriverEvent.Authorized)
        composeRule.onNodeWithTag("qlz_scan_action").assertIsNotEnabled()
    }

    private fun startMeasurement() {
        beginScan()
        emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(driver.device)))
        click("qlz_device_${driver.device.id}")
        composeRule.runOnIdle {
            assertEquals(listOf(driver.device.id), driver.connections)
            assertTrue(driver.stops > 0)
        }
        emit(QlzEvaluationDriverEvent.Connected(driver.device.displayName, driver.device.id))
        emit(QlzEvaluationDriverEvent.CheckStarted)
    }

    private fun emit(event: QlzEvaluationDriverEvent) {
        composeRule.runOnIdle { driver.emit(event) }
        composeRule.waitForIdle()
    }

    private fun click(tag: String) {
        // Production singleClick uses elapsedRealtime, not Compose's animation clock.
        lastClickByTag[tag]?.let { previous ->
            composeRule.waitUntil(timeoutMillis = 2_000) {
                SystemClock.elapsedRealtime() - previous >= 500
            }
        }
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        lastClickByTag[tag] = SystemClock.elapsedRealtime()
    }

    private class ScriptedDriver : QlzEvaluationDriver {
        val device = QlzDeviceOption("mock-device", "Mock 检测设备", "••••")
        val connections = mutableListOf<String>()
        var scans = 0
        var stops = 0
        var reconnections = 0
        var uploads = 0
        var retries = 0
        var closes = 0
        private lateinit var listener: (QlzEvaluationDriverEvent) -> Unit
        fun emit(event: QlzEvaluationDriverEvent) = listener(event)
        override fun authorize(token: String, listener: (QlzEvaluationDriverEvent) -> Unit) {
            check(token == "mock-token")
            this.listener = listener
        }
        override fun startScan() { scans++ }
        override fun stopScan() { stops++ }
        override fun connect(deviceId: String) { connections += deviceId }
        override fun reconnect() { reconnections++ }
        override fun upload(context: QlzEvaluationUploadContext) { uploads++ }
        override fun retryUpload() { retries++ }
        override fun abortCheck() = Unit
        override fun close() { closes++ }
    }
}
