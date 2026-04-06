package com.ytone.longcare.features.nfctest.ui

import com.ytone.longcare.features.nfctest.vm.TypeCRfidPanelState
import com.ytone.longcare.features.nfctest.vm.UsbProbeUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeCRfidTestPanelStateTest {

    @Test
    fun `read failed state exposes message for UI`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.ReadFailed("timeout"),
        )

        val message = (state.probeState as UsbProbeUiState.ReadFailed).message
        assertEquals("timeout", message)
    }
}
