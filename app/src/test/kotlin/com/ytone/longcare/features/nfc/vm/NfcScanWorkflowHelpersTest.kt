package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import org.junit.Assert.*
import org.junit.Test

class NfcScanWorkflowHelpersTest {
    @Test
    fun `reduceReaderUiState keeps business and device state separate`() {
        assertTrue(
            reduceReaderUiState(
                currentMode = ScanMode.EXTERNAL_RFID,
                event = AppEvent.ReaderConnectionChanged(connected = false),
                currentReaderState = ReaderUiState.Ready,
            ) is ReaderUiState.Disconnected,
        )
    }

    @Test
    fun `reduceReaderUiState turns reader errors into device error for external mode`() {
        val next = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderError("R65C读卡异常，请重试", ScanSource.EXTERNAL_RFID),
            currentReaderState = ReaderUiState.Ready,
        )

        assertEquals(ReaderUiState.DeviceError("R65C读卡异常，请重试"), next)
    }

    @Test
    fun `reduceReaderUiState ignores non-active-source reader events`() {
        val unchangedFromConnection = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderConnectionChanged(connected = true, source = ScanSource.SYSTEM_NFC),
            currentReaderState = ReaderUiState.Disconnected,
        )
        val unchangedFromError = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderError(message = "system error", source = ScanSource.SYSTEM_NFC),
            currentReaderState = ReaderUiState.Ready,
        )

        assertTrue(unchangedFromConnection is ReaderUiState.Disconnected)
        assertTrue(unchangedFromError is ReaderUiState.Ready)
    }

}
