package com.ytone.longcare.integration.qlz

import com.evenmed.sdk.call.ErrorCodeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzEvaluationReducerTest {
    @Test
    fun `reducer accepts valid scan flow and rejects out of order progress`() {
        val idle = QlzEvaluationUiState()
        assertEquals(
            idle,
            QlzEvaluationReducer.reduce(
                idle,
                QlzEvaluationDriverEvent.ProgressChanged(2, 10),
            ),
        )

        val authorizing = idle.copy(stage = QlzEvaluationStage.AUTHORIZING)
        val ready =
            QlzEvaluationReducer.reduce(
                authorizing,
                QlzEvaluationDriverEvent.Authorized,
            )
        val scanning =
            QlzEvaluationReducer.reduce(
                ready,
                QlzEvaluationDriverEvent.ScanStarted,
            )
        val withDevice =
            QlzEvaluationReducer.reduce(
                scanning,
                QlzEvaluationDriverEvent.DevicesChanged(
                    listOf(QlzDeviceOption("device-1", "BM-S", "••:••:••:••:AA:BB"))
                ),
            )

        assertEquals(QlzEvaluationStage.READY_TO_SCAN, ready.stage)
        assertEquals(QlzEvaluationStage.SCANNING, scanning.stage)
        assertEquals(QlzEvaluationStage.SCAN_RESULTS, withDevice.stage)
        assertEquals(1, withDevice.devices.size)
    }

    @Test
    fun `finger snapshot is always five values and does not retain mutable array`() {
        val callbackArray = booleanArrayOf(true, false, true)
        val snapshot = QlzFingerContacts.from(callbackArray)
        callbackArray[0] = false

        assertEquals(
            listOf(true, false, true, false, false),
            snapshot.asList(),
        )
        assertFalse(snapshot.allConnected)
        assertTrue(
            QlzFingerContacts.from(BooleanArray(5) { true }).allConnected
        )
    }

    @Test
    fun `progress is bounded before it reaches UI`() {
        val measuring = QlzEvaluationUiState(stage = QlzEvaluationStage.MEASURING)
        val state =
            QlzEvaluationReducer.reduce(
                measuring,
                QlzEvaluationDriverEvent.ProgressChanged(20, 8),
            )

        assertEquals(8, state.successCount)
        assertEquals(8, state.totalCount)
        assertEquals(1f, state.progressFraction)
    }

    @Test
    fun `device catalog deduplicates addresses and never exposes a raw identifier`() {
        val catalog = QlzDeviceCatalog<String>()
        val reusedCallbackList =
            mutableListOf(
                QlzScannedDevice("BM-S 1", "AA:BB:CC:DD:EE:FF", "first"),
                QlzScannedDevice("duplicate", "aa:bb:cc:dd:ee:ff", "duplicate"),
                QlzScannedDevice("BM-E 2", "11:22:33:44:55:66", "second"),
            )

        val firstSnapshot = catalog.snapshot(reusedCallbackList)
        reusedCallbackList.clear()
        val emptySnapshot = catalog.snapshot(reusedCallbackList)

        assertEquals(2, firstSnapshot.size)
        assertEquals("••:••:••:••:EE:FF", firstSnapshot[0].maskedIdentifier)
        assertFalse(firstSnapshot[0].id.contains("AA:BB"))
        assertFalse(firstSnapshot.toString().contains("AA:BB:CC:DD:EE:FF"))
        assertEquals("first", catalog.resolve(firstSnapshot[0].id))
        assertTrue(emptySnapshot.isEmpty())
        assertEquals(2, firstSnapshot.size)
    }

    @Test
    fun `known vendor failures map to bounded user recovery actions`() {
        assertEquals(
            QlzEvaluationIssue.LOW_POWER,
            mapQlzFailure(ErrorCodeConfig.error_check_device_power_low).issue,
        )
        assertEquals(
            QlzEvaluationRecoveryAction.RETRY_CONNECTION,
            mapQlzFailure(ErrorCodeConfig.error_check_device_sigle).recoveryAction,
        )
        assertEquals(
            QlzEvaluationRecoveryAction.RETRY_UPLOAD,
            mapQlzFailure(ErrorCodeConfig.error_check_up_neterror).recoveryAction,
        )
        assertEquals(
            QlzEvaluationIssue.NO_MEASUREMENT_DATA,
            mapQlzFailure(ErrorCodeConfig.error_check_data_null).issue,
        )
        assertEquals(
            QlzEvaluationIssue.NETWORK,
            mapQlzFailure(ErrorCodeConfig.error_server_outtime).issue,
        )
        val unknown = mapQlzFailure(Int.MAX_VALUE)
        assertEquals(QlzEvaluationIssue.UNKNOWN, unknown.issue)
        assertNotEquals(QlzEvaluationRecoveryAction.RETRY_UPLOAD, unknown.recoveryAction)
        assertNull(
            evaluateQlzBluetoothEnvironment(
                QlzBluetoothEnvironmentSnapshot(
                    apiLevel = 31,
                    hasBleFeature = true,
                    permissionsGranted = true,
                    hasBluetoothAdapter = true,
                    bluetoothEnabled = true,
                    locationServiceEnabled = false,
                )
            )
        )
    }

    @Test
    fun `charging pauses and unplugging resumes a five-finger measurement`() {
        val measuring =
            QlzEvaluationUiState(
                stage = QlzEvaluationStage.MEASURING,
                fingerContacts = QlzFingerContacts(little = true),
            )
        val paused =
            QlzEvaluationReducer.reduce(
                measuring,
                QlzEvaluationDriverEvent.PowerChanged(true),
            )
        val resumed =
            QlzEvaluationReducer.reduce(
                paused,
                QlzEvaluationDriverEvent.PowerChanged(false),
            )

        assertEquals(QlzEvaluationStage.POWER_PAUSED, paused.stage)
        assertEquals(QlzEvaluationIssue.CHARGING, paused.issue)
        assertEquals(QlzEvaluationStage.MEASURING, resumed.stage)
        assertNull(resumed.issue)
        assertTrue(resumed.fingerContacts.little)
    }

    @Test
    fun `upload buffer accepts one result and clears retry payload after success`() {
        val buffer = QlzUploadBuffer<String, String>()

        assertTrue(buffer.acceptMeasurements(mutableListOf("measurement")))
        assertFalse(buffer.acceptMeasurements(listOf("duplicate")))
        assertEquals(listOf("measurement"), buffer.beginInitialUpload())
        assertNull(buffer.beginInitialUpload())
        buffer.recordFailure("retry-record")
        assertTrue(buffer.hasRetryPayload())
        assertEquals("retry-record", buffer.beginRetry())
        assertNull(buffer.beginRetry())
        buffer.complete()

        assertFalse(buffer.hasRetryPayload())
        assertNull(buffer.beginInitialUpload())
        assertNull(buffer.beginRetry())
    }

    @Test
    fun `payment blocker cannot be replaced by a late disconnect callback`() {
        val measuring = QlzEvaluationUiState(stage = QlzEvaluationStage.MEASURING)
        val blocked =
            QlzEvaluationReducer.reduce(
                measuring,
                QlzEvaluationDriverEvent.PaymentRequired,
            )
        val afterDisconnect =
            QlzEvaluationReducer.reduce(
                blocked,
                QlzEvaluationDriverEvent.Failed(
                    code = 12,
                    issue = QlzEvaluationIssue.CONNECTION_LOST,
                    recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
                ),
            )

        assertEquals(QlzEvaluationStage.BLOCKED, blocked.stage)
        assertEquals(QlzEvaluationIssue.PAYMENT_REQUIRED, blocked.issue)
        assertEquals(blocked, afterDisconnect)
    }
}
