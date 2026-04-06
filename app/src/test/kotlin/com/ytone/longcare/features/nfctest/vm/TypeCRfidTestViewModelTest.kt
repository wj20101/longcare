package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostDeviceEvent
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import com.ytone.longcare.common.utils.UsbDeviceSummary
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypeCRfidTestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = "12:34:56"

    private fun sampleSummary() = UsbDeviceSummary(
        deviceName = "reader-1",
        vendorId = 1234,
        productId = 5678,
        deviceClass = 0,
        deviceSubclass = 0,
        deviceProtocol = 0,
        interfaceCount = 1,
        endpoints = emptyList(),
    )

    @Test
    fun `refresh updates state to no device when probe manager finds nothing`() {
        val probeManager = mockk<UsbHostProbeManager>()
        every { probeManager.refresh() } returns UsbHostProbeResult.NoDevice
        every { probeManager.observeDeviceChanges() } returns emptyFlow()

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
        )

        viewModel.refreshDevices()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.NoDevice)
    }

    @Test
    fun `refresh maps device found with permission to ready and stores last updated time`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val summary = sampleSummary()
        every { probeManager.observeDeviceChanges() } returns emptyFlow()
        every { probeManager.refresh() } returns UsbHostProbeResult.DeviceFound(
            summary = summary,
            hasPermission = true,
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.refreshDevices()
        val panelState = viewModel.panelState.value

        assertTrue(panelState.probeState is UsbProbeUiState.Ready)
        assertEquals(summary, panelState.deviceSummary)
        assertEquals(fixedNow, panelState.lastUpdatedAt)
    }

    @Test
    fun `refresh maps device found without permission to device detected and stores last updated time`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val summary = sampleSummary()
        every { probeManager.observeDeviceChanges() } returns emptyFlow()
        every { probeManager.refresh() } returns UsbHostProbeResult.DeviceFound(
            summary = summary,
            hasPermission = false,
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.refreshDevices()
        val panelState = viewModel.panelState.value

        assertTrue(panelState.probeState is UsbProbeUiState.DeviceDetected)
        assertEquals(summary, panelState.deviceSummary)
        assertEquals(fixedNow, panelState.lastUpdatedAt)
    }

    @Test
    fun `attempt read maps read failure message preserves summary and stores last updated time`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val activity = mockk<Activity>(relaxed = true)
        val summary = sampleSummary()
        val message = "read failed"
        every { probeManager.observeDeviceChanges() } returns emptyFlow()
        every { probeManager.attemptRead(activity) } returns UsbHostProbeResult.ReadFailure(
            summary = summary,
            message = message,
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.attemptRead(activity)
        val panelState = viewModel.panelState.value

        assertTrue(panelState.probeState is UsbProbeUiState.ReadFailed)
        assertEquals(message, (panelState.probeState as UsbProbeUiState.ReadFailed).message)
        assertEquals(summary, panelState.deviceSummary)
        assertEquals(fixedNow, panelState.lastUpdatedAt)
    }

    @Test
    fun `attempt read maps usb permission failure to permission denied`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val activity = mockk<Activity>(relaxed = true)
        val summary = sampleSummary()

        every { probeManager.observeDeviceChanges() } returns emptyFlow()
        every { probeManager.attemptRead(activity) } returns UsbHostProbeResult.ReadFailure(
            summary = summary,
            message = "USB权限未授予",
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.attemptRead(activity)

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.PermissionDenied)
        assertEquals(summary, viewModel.panelState.value.deviceSummary)
        assertEquals(fixedNow, viewModel.panelState.value.lastUpdatedAt)
    }

    @Test
    fun `attempt read stores raw payload hex text and parsed tag id`() {
        val probeManager = mockk<UsbHostProbeManager>()
        val parser = mockk<ExternalRfidTagParser>()
        val activity = mockk<Activity>(relaxed = true)
        val summary = sampleSummary()

        every { probeManager.observeDeviceChanges() } returns emptyFlow()
        every { probeManager.attemptRead(activity) } returns UsbHostProbeResult.ReadSuccess(
            summary = summary,
            payload = "ABC123".encodeToByteArray(),
        )
        every { parser.normalize("ABC123") } returns "ABC123"

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = parser,
            nowProvider = { fixedNow },
        )
        viewModel.attemptRead(activity)

        assertEquals("ABC123", viewModel.panelState.value.rawPayloadText)
        assertEquals("41 42 43 31 32 33", viewModel.panelState.value.rawPayloadHex)
        assertEquals("ABC123", viewModel.panelState.value.parsedTagId)
        assertEquals(fixedNow, viewModel.panelState.value.lastUpdatedAt)
    }

    @Test
    fun `device attach event reuses refresh path`() = runTest {
        val probeManager = mockk<UsbHostProbeManager>()
        val deviceEvents = MutableSharedFlow<UsbHostDeviceEvent>(extraBufferCapacity = 1)
        val summary = sampleSummary()

        every { probeManager.observeDeviceChanges() } returns deviceEvents
        every { probeManager.refresh() } returns UsbHostProbeResult.DeviceFound(
            summary = summary,
            hasPermission = true,
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.startObserving()
        advanceUntilIdle()
        deviceEvents.emit(UsbHostDeviceEvent.Attached)
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.Ready)
        assertEquals(summary, viewModel.panelState.value.deviceSummary)
    }

    @Test
    fun `device detach event reuses refresh path`() = runTest {
        val probeManager = mockk<UsbHostProbeManager>()
        val deviceEvents = MutableSharedFlow<UsbHostDeviceEvent>(extraBufferCapacity = 1)

        every { probeManager.observeDeviceChanges() } returns deviceEvents
        every { probeManager.refresh() } returns UsbHostProbeResult.NoDevice

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.startObserving()
        advanceUntilIdle()
        deviceEvents.emit(UsbHostDeviceEvent.Detached)
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.NoDevice)
    }

    @Test
    fun `permission denied event updates panel state without requiring manual refresh`() = runTest {
        val probeManager = mockk<UsbHostProbeManager>()
        val deviceEvents = MutableSharedFlow<UsbHostDeviceEvent>(extraBufferCapacity = 1)

        every { probeManager.observeDeviceChanges() } returns deviceEvents

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.startObserving()
        advanceUntilIdle()
        deviceEvents.emit(UsbHostDeviceEvent.PermissionChanged(granted = false))
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.PermissionDenied)
        assertEquals(fixedNow, viewModel.panelState.value.lastUpdatedAt)
    }
}
