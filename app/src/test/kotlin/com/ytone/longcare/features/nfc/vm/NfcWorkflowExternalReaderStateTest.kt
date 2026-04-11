package com.ytone.longcare.features.nfc.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowExternalReaderStateTest {

    @Test
    fun `external reader ready seeds ready state`() {
        val state = initialExternalReaderUiState(isReaderReady = true)

        assertEquals(ReaderUiState.Ready, state)
    }

    @Test
    fun `external reader not ready seeds disconnected state`() {
        val state = initialExternalReaderUiState(isReaderReady = false)

        assertEquals(ReaderUiState.Disconnected, state)
    }
}
