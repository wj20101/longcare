package com.ytone.longcare.features.nfc.ui

import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowUiCopyTest {

    @Test
    fun `external disconnected copy instructs the user to connect a type c reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Disconnected,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT, copy.bottomHintKey)
    }

    @Test
    fun `external ready copy tells the user to scan on the reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Ready,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_HINT, copy.bottomHintKey)
    }
}
