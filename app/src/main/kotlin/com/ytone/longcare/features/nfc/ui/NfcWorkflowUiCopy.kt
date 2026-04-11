package com.ytone.longcare.features.nfc.ui

import androidx.annotation.StringRes
import com.ytone.longcare.R
import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode

internal enum class NfcWorkflowCopyKey {
    SYSTEM_IDLE_PROMPT,
    SYSTEM_IDLE_STATUS,
    SYSTEM_IDLE_HINT,
    EXTERNAL_DISCONNECTED_PROMPT,
    EXTERNAL_DISCONNECTED_STATUS,
    EXTERNAL_DISCONNECTED_HINT,
    EXTERNAL_READY_PROMPT,
    EXTERNAL_READY_STATUS,
    EXTERNAL_READY_HINT,
    EXTERNAL_READING_PROMPT,
    EXTERNAL_READING_STATUS,
}

internal data class NfcWorkflowIdleCopy(
    val promptKey: NfcWorkflowCopyKey,
    val statusKey: NfcWorkflowCopyKey,
    val bottomHintKey: NfcWorkflowCopyKey,
)

internal fun resolveNfcWorkflowIdleCopy(
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
): NfcWorkflowIdleCopy = when (scanMode) {
    ScanMode.SYSTEM_NFC -> NfcWorkflowIdleCopy(
        promptKey = NfcWorkflowCopyKey.SYSTEM_IDLE_PROMPT,
        statusKey = NfcWorkflowCopyKey.SYSTEM_IDLE_STATUS,
        bottomHintKey = NfcWorkflowCopyKey.SYSTEM_IDLE_HINT,
    )

    ScanMode.EXTERNAL_RFID -> when (readerUiState) {
        ReaderUiState.Reading -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_READING_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_READING_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_READY_HINT,
        )

        ReaderUiState.Ready -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_READY_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_READY_HINT,
        )

        ReaderUiState.NotRequired,
        ReaderUiState.Disconnected,
        is ReaderUiState.DeviceError,
        -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT,
        )
    }
}

@StringRes
internal fun resolveCopyRes(key: NfcWorkflowCopyKey): Int = when (key) {
    NfcWorkflowCopyKey.SYSTEM_IDLE_PROMPT -> R.string.nfc_sign_in_prompt
    NfcWorkflowCopyKey.SYSTEM_IDLE_STATUS -> R.string.nfc_sign_in_idle_hint
    NfcWorkflowCopyKey.SYSTEM_IDLE_HINT -> R.string.nfc_sign_in_idle_hint
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT -> R.string.nfc_external_reader_prompt
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS -> R.string.nfc_external_reader_disconnected
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT -> R.string.nfc_external_reader_disconnected_hint
    NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT -> R.string.nfc_external_reader_ready_prompt
    NfcWorkflowCopyKey.EXTERNAL_READY_STATUS -> R.string.nfc_external_reader_ready
    NfcWorkflowCopyKey.EXTERNAL_READY_HINT -> R.string.nfc_external_reader_ready_hint
    NfcWorkflowCopyKey.EXTERNAL_READING_PROMPT -> R.string.nfc_external_reader_reading_prompt
    NfcWorkflowCopyKey.EXTERNAL_READING_STATUS -> R.string.nfc_external_reader_reading
}
