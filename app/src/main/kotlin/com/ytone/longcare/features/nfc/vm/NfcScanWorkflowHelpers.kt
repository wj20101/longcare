package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource

internal fun reduceReaderUiState(
    currentMode: ScanMode,
    event: AppEvent,
    currentReaderState: ReaderUiState,
): ReaderUiState = when {
    currentMode == ScanMode.SYSTEM_NFC -> ReaderUiState.NotRequired
    event is AppEvent.ReaderConnectionChanged && event.source == activeScanSource(currentMode) && event.connected -> ReaderUiState.Ready
    event is AppEvent.ReaderConnectionChanged && event.source == activeScanSource(currentMode) && !event.connected -> ReaderUiState.Disconnected
    event is AppEvent.ReaderError && event.source == activeScanSource(currentMode) -> ReaderUiState.DeviceError(event.message)
    else -> currentReaderState
}

private fun activeScanSource(mode: ScanMode): ScanSource = when (mode) {
    ScanMode.SYSTEM_NFC -> ScanSource.SYSTEM_NFC
    ScanMode.EXTERNAL_RFID -> ScanSource.EXTERNAL_RFID
}
