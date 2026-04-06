package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import io.mockk.every
import io.mockk.mockk
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
}
