package com.ytone.longcare.features.nfc.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowViewModelR65cFallbackTest {

    @Test
    fun `reading transitions to ready on fallback submit`() {
        val next = nextReaderUiStateAfterR65cFallbackSubmit(ReaderUiState.Reading)

        assertEquals(ReaderUiState.Ready, next)
    }

    @Test
    fun `disconnected stays disconnected on fallback submit`() {
        val next = nextReaderUiStateAfterR65cFallbackSubmit(ReaderUiState.Disconnected)

        assertEquals(ReaderUiState.Disconnected, next)
    }

    @Test
    fun `device error stays device error on fallback submit`() {
        val current = ReaderUiState.DeviceError("reader failed")
        val next = nextReaderUiStateAfterR65cFallbackSubmit(current)

        assertEquals(current, next)
    }
}
