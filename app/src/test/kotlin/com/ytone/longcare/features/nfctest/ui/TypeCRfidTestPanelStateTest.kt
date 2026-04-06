package com.ytone.longcare.features.nfctest.ui

import com.ytone.longcare.common.utils.UsbDeviceSummary
import com.ytone.longcare.common.utils.UsbEndpointSummary
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

    @Test
    fun `format permission state distinguishes pending denied and granted`() {
        assertEquals("待申请", formatPermissionState(UsbProbeUiState.DeviceDetected))
        assertEquals("未授予", formatPermissionState(UsbProbeUiState.PermissionDenied))
        assertEquals("已授予", formatPermissionState(UsbProbeUiState.Ready))
    }

    @Test
    fun `format device class info and endpoints exposes probe details`() {
        val summary = UsbDeviceSummary(
            deviceName = "reader-1",
            vendorId = 0x1234,
            productId = 0x5678,
            deviceClass = 3,
            deviceSubclass = 1,
            deviceProtocol = 2,
            interfaceCount = 1,
            endpoints = listOf(
                UsbEndpointSummary(
                    address = 0x81,
                    direction = 128,
                    type = 2,
                    maxPacketSize = 64,
                )
            ),
        )

        assertEquals("3 / 1 / 2", formatDeviceClassInfo(summary))
        assertEquals("addr=0x81 dir=128 type=2 size=64", formatEndpoints(summary.endpoints))
    }
}
