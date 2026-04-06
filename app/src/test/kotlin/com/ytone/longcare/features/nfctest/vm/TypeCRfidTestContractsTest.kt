package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class TypeCRfidTestContractsTest {

    @Test
    fun `raw payload hex is uppercase and space separated`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.Ready,
            rawPayload = byteArrayOf(0x01, 0x0A, 0x2F),
        )

        assertEquals("01 0A 2F", state.rawPayloadHex)
    }

    @Test
    fun `parsed tag id falls back to not parsed when parser returns null`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.ReadFailed("timeout"),
            parsedTagId = null,
        )

        assertEquals("未解析出卡号", state.parsedTagDisplay)
    }
}
