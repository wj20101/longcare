package com.ytone.longcare.integration.qlz

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QlzGripPreparationTest {
    private val held = QlzEvaluationUiState(
        stage = QlzEvaluationStage.MEASURING,
        fingerContacts = QlzFingerContacts(true, true, true, true, true),
    )

    @Test fun percentageUsesExactCountsWithoutFloatRoundingLoss() {
        for (percent in 0..100) {
            assertEquals(percent, held.copy(successCount = percent, totalCount = 100).progressPercent)
        }
        assertEquals(0, held.copy(successCount = 10, totalCount = 0).progressPercent)
        assertEquals(100, held.copy(successCount = Int.MAX_VALUE, totalCount = Int.MAX_VALUE).progressPercent)
        assertEquals(0, held.copy(successCount = -1, totalCount = 100).progressPercent)
        assertEquals(100, held.copy(successCount = 101, totalCount = 100).progressPercent)
    }

    @Test fun countdownUsesDeadlineAndDoesNotRestartOnRepeatedState() {
        val preparation = QlzGripPreparation()
        assertEquals(5, preparation.update(held, true, 100).preparationSeconds)
        assertEquals(4, preparation.update(held, true, 1100).preparationSeconds)
        assertEquals(1, preparation.update(held, true, 4100).preparationSeconds)
        val finished = preparation.update(held, true, 5100)
        assertNull(finished.preparationSeconds)
        assertTrue(finished.showMeasurementProgress)
        assertEquals(0, finished.totalCount)
        assertEquals(QlzEvaluationStage.MEASURING, finished.stage)
    }

    @Test fun lostContactAndBackgroundRequireFreshPreparation() {
        val preparation = QlzGripPreparation()
        preparation.update(held, true, 0)
        assertNull(preparation.update(held.copy(fingerContacts = QlzFingerContacts()), true, 500).preparationSeconds)
        assertEquals(5, preparation.update(held, true, 900).preparationSeconds)
        assertNull(preparation.update(held, false, 1200).preparationSeconds)
        assertEquals(5, preparation.update(held, true, 6000).preparationSeconds)
    }

    @Test fun genuineProgressPreemptsCountdownAndDoesNotRestartOnContactJitter() {
        val preparation = QlzGripPreparation()
        preparation.update(held, true, 0)
        val sampled = preparation.update(held.copy(totalCount = 100, successCount = 46), true, 100)
        assertNull(sampled.preparationSeconds)
        assertTrue(sampled.showMeasurementProgress)
        assertTrue(preparation.update(held, true, 8000).showMeasurementProgress)
        preparation.reset()
        assertEquals(5, preparation.update(held, true, 9000).preparationSeconds)
    }

    @Test fun interruptionCancelsCountdown() {
        for (stage in listOf(QlzEvaluationStage.ERROR, QlzEvaluationStage.POWER_PAUSED,
            QlzEvaluationStage.UPLOADING, QlzEvaluationStage.CLOSED)) {
            val preparation = QlzGripPreparation()
            preparation.update(held, true, 0)
            assertNull(preparation.update(held.copy(stage = stage), true, 500).preparationSeconds)
        }
    }

    @Test fun sessionTicksAndCancelsWithoutStartingOrUploadingMeasurement() = runTest {
        val driver = GripDriver()
        val session = QlzEvaluationSession(
            QlzEvaluationDriverFactory { driver }, QlzEvaluationUploadContext(), {},
            releaseLease = {}, presentationScope = backgroundScope,
            nowMillis = { testScheduler.currentTime },
        )
        val device = QlzDeviceOption("mock", "Mock", "••:AA")
        session.start("mock-token")
        driver.emit(QlzEvaluationDriverEvent.Authorized)
        driver.emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(device)))
        session.selectDevice(device.id)
        driver.emit(QlzEvaluationDriverEvent.Connected(device.displayName, device.id))
        driver.emit(QlzEvaluationDriverEvent.CheckStarted)
        driver.emit(QlzEvaluationDriverEvent.FingerContactsChanged(held.fingerContacts))
        assertEquals(5, session.state.value.preparationSeconds)
        advanceTimeBy(2000); runCurrent()
        assertEquals(3, session.state.value.preparationSeconds)
        session.onHostStopped()
        advanceTimeBy(6000); runCurrent()
        assertNull(session.state.value.preparationSeconds)
        session.onHostStarted()
        assertFalse(session.state.value.fingerContacts.allConnected)
        driver.emit(QlzEvaluationDriverEvent.FingerContactsChanged(held.fingerContacts))
        advanceTimeBy(5000); runCurrent()
        assertTrue(session.state.value.showMeasurementProgress)
        assertTrue(driver.uploads.isEmpty())
        session.close()
        advanceTimeBy(1000)
        assertEquals(QlzEvaluationStage.CLOSED, session.state.value.stage)
    }

    private class GripDriver : QlzEvaluationDriver {
        lateinit var listener: (QlzEvaluationDriverEvent) -> Unit
        val uploads = mutableListOf<QlzEvaluationUploadContext>()
        fun emit(event: QlzEvaluationDriverEvent) = listener(event)
        override fun authorize(token: String, listener: (QlzEvaluationDriverEvent) -> Unit) { this.listener = listener }
        override fun startScan() = Unit
        override fun stopScan() = Unit
        override fun connect(deviceId: String) = Unit
        override fun reconnect() = Unit
        override fun upload(context: QlzEvaluationUploadContext) { uploads += context }
        override fun retryUpload() = Unit
        override fun abortCheck() = Unit
        override fun close() = Unit
    }
}
