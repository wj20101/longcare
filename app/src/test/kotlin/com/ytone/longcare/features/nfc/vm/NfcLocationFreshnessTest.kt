package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcLocationFreshnessTest {
    private val facade = mockk<LocationFacade>()
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val messages = NfcUserMessages("network", "detail", "bind")
    private val text = mockk<ResourceTextResolver> { every { text(any()) } returns "location expired" }
    private val delegate = NfcOrderWorkflowDelegate(facade, text, lifecycle, orders,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), state, messages)
    private val sample = LocationResult(31.0, 121.0, "test")
    private val key = OrderKey(1)

    @Test fun `expired start location never calls check order`() = runTest {
        every { facade.isUsable(sample) } returns false
        delegate.startOrder(key, "tag", sample)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        assertEquals("location expired", (state.value as NfcSignInUiState.Error).message)
        every { facade.isUsable(sample) } returns true
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        delegate.startOrder(key, "tag", sample)
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.0", "31.0") }
    }

    @Test fun `location expiring during end check prevents submit and keeps service active`() = runTest {
        var valid = true
        every { facade.isUsable(sample) } answers { valid }
        coEvery { orders.checkEndOrder(any(), any()) } coAnswers {
            delay(16_000)
            valid = false
            ApiResult.Success(Unit)
        }
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), location = sample)
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
        assertTrue(state.value is NfcSignInUiState.Error)
    }

    @Test fun `expired bind must reacquire and show confirmation again`() = runTest {
        val refreshed = sample.copy(locationTime = 1234)
        every { facade.isUsable(sample) } returns false
        every { facade.isUsable(refreshed) } returns true
        val pending = MutableStateFlow<PendingNfcData?>(PendingNfcData(key, SignInMode.START_ORDER, null, "tag", sample))
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        val scan = NfcScanWorkflowDelegate(facade, { LocationRequestResult.Coordinates(refreshed) },
            AppEventBus(), mockk(relaxed = true), orders, backgroundScope, state, pending,
            MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired), delegate, messages)
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        coVerify(exactly = 0) { orders.bindLocation(any(), any(), any(), any()) }
        assertEquals(refreshed, pending.value?.location)
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        coVerify(exactly = 1) { orders.bindLocation(1, "tag", "121.0", "31.0") }
        scan.clear()
    }

    @Test fun `entering and listening does not acquire location only first tag does`() = runTest {
        val bus = AppEventBus()
        var requests = 0
        val scan = NfcScanWorkflowDelegate(facade, { error("not used") }, bus, mockk(relaxed = true),
            orders, backgroundScope, state, MutableStateFlow(null),
            MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired),
            mockk(relaxed = true), messages)
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) {
            requests++
            awaitCancellation()
        }
        runCurrent()
        assertEquals(0, requests)
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        assertEquals(1, requests)
        scan.clear()
        runCurrent()
        assertEquals(NfcSignInUiState.Initial, state.value)
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) {
            requests++
            awaitCancellation()
        }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        assertEquals(2, requests)
        scan.clear()
    }

    @Test fun `permission denial resets scan and a new tag can resume only once`() = runTest {
        val bus = AppEventBus()
        val scan = NfcScanWorkflowDelegate(facade, { error("not used") }, bus, mockk(relaxed = true),
            orders, backgroundScope, state, MutableStateFlow(null),
            MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired),
            delegate, messages)
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { LocationRequestResult.PermissionRequired }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        assertEquals(NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION), state.value)
        scan.clearPendingPermissionScan()
        assertEquals(NfcSignInUiState.Initial, state.value)
        assertFalse(scan.resumePendingPermissionScan { error("denied request must be cleared") })
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        var requests = 0
        assertTrue(scan.resumePendingPermissionScan { requests++; LocationRequestResult.PermissionRequired })
        runCurrent()
        assertEquals(NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION), state.value)
        assertTrue(scan.resumePendingPermissionScan {
            requests++
            LocationRequestResult.Error("retryable location failure")
        })
        assertFalse(scan.resumePendingPermissionScan { error("duplicate callback") })
        runCurrent()
        assertEquals(2, requests)
        assertEquals("retryable location failure", (state.value as NfcSignInUiState.Error).message)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        scan.clear()
    }

    @Test fun `start request keeps coordinate order and full double precision`() = runTest {
        val precise = sample.copy(latitude = 31.23456789012345, longitude = 121.98765432109876)
        every { facade.isUsable(precise) } returns true
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        delegate.startOrder(key, "tag", precise)
        coVerify(exactly = 1) {
            orders.checkOrder(1, "tag", precise.longitude.toString(), precise.latitude.toString())
        }
    }
}
