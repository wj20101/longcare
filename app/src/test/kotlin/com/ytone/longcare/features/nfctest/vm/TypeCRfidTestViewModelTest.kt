package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import com.ytone.longcare.common.utils.UsbDeviceSummary
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeCRfidTestViewModelTest {

    @Test
    fun `refresh updates state to no device when probe manager finds nothing`() {
        val probeManager = mockk<UsbHostProbeManager>()
        every { probeManager.refresh() } returns UsbHostProbeResult.NoDevice

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
        )

        viewModel.refreshDevices()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.NoDevice)
    }

    @Test
    fun `attempt read stores raw payload hex text and parsed tag id`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val parser = mockk<ExternalRfidTagParser>()
        val activity = mockk<Activity>(relaxed = true)
        val summary = UsbDeviceSummary(
            deviceName = "reader-1",
            vendorId = 1234,
            productId = 5678,
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaceCount = 1,
            endpoints = emptyList(),
        )

        every { probeManager.attemptRead(activity) } returns UsbHostProbeResult.ReadSuccess(
            summary = summary,
            payload = "ABC123".encodeToByteArray(),
        )
        every { parser.normalize("ABC123") } returns "ABC123"

        val viewModel = TypeCRfidTestViewModel(probeManager, parser)
        viewModel.attemptRead(activity)

        assertEquals("ABC123", viewModel.panelState.value.rawPayloadText)
        assertEquals("41 42 43 31 32 33", viewModel.panelState.value.rawPayloadHex)
        assertEquals("ABC123", viewModel.panelState.value.parsedTagId)
    }
}
