package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcBindingLocationSessionTest {
    private val bus = AppEventBus()
    private val facade = mockk<LocationFacade>()
    private val orders = mockk<OrderRepository>()
    private val details = mockk<OrderDetailRepository>()
    private val text = mockk<ResourceTextResolver> { every { text(any()) } returns "location failed" }
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val pending = MutableStateFlow<PendingNfcData?>(null)
    private val messages = NfcUserMessages("network", "detail", "bind", "unavailable")
    private val key = OrderKey(1)
    private val sample = LocationResult(31.0, 121.0, "test", 10f, "GCJ02", 5, 1,
        locationTime = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(), receivedElapsedRealtime = 1_000)
    private val fresh = sample.copy(latitude = 31.001, longitude = 121.001)
    private val orderDelegate = NfcOrderWorkflowDelegate(facade, text, mockk(relaxed = true), orders,
        details, mockk(relaxed = true), mockk(relaxed = true), state, messages)

    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000
        every { facade.serviceOrderLocationFailure(any()) } returns null
        // Deliberately retain the pre-bind cache: retries must honor the confirmed server outcome.
        every { details.getCachedOrderInfo(any()) } returns ServiceOrderInfoModel()
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `slow binding completes before acquiring a separate current sign-in point`() = runTest {
        val binding = CompletableDeferred<ApiResult<Unit>>()
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers { binding.await() }
        var requests = 0
        val scan = beginBinding {
            LocationRequestResult.Coordinates(if (++requests == 1) sample else fresh)
        }
        assertEquals(1, requests)
        advanceTimeBy(60_000)
        runCurrent()
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        binding.complete(ApiResult.Success(Unit))
        runCurrent()

        assertEquals(2, requests)
        coVerify(exactly = 1) { orders.bindLocation(1, "tag", "121.0", "31.0") }
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.001", "31.001") }
        assertTrue(state.value is NfcSignInUiState.Success)
        scan.clear()
    }

    @Test fun `post-bind precision failure retries the retained tag without binding again`() = runTest {
        var requests = 0
        val scan = beginBinding {
            if (++requests == 1) LocationRequestResult.Coordinates(sample)
            else LocationRequestResult.Error("precision", reason = LocationFailure.PRECISION)
        }
        assertTrue(state.value is NfcSignInUiState.Error)
        assertNull(pending.value)
        assertTrue(scan.retryLocation { requests++; LocationRequestResult.Coordinates(fresh) })
        runCurrent()

        assertEquals(3, requests)
        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.001", "31.001") }
        assertFalse(scan.retryLocation { error("Completed business requests must not replay") })
        scan.clear()
    }

    @Test fun `post-bind permission recovery reacquires without reopening binding`() = runTest {
        var requests = 0
        val scan = beginBinding {
            if (++requests == 1) LocationRequestResult.Coordinates(sample)
            else LocationRequestResult.PermissionRequired
        }
        assertEquals(NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION), state.value)
        assertTrue(scan.resumePendingPermissionScan { LocationRequestResult.Coordinates(fresh) })
        assertFalse(scan.resumePendingPermissionScan { error("Duplicate permission callback") })
        runCurrent()

        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.001", "31.001") }
        assertNull(pending.value)
        scan.clear()
    }

    @Test fun `sign-in business failure requires another scan but cannot repeat confirmed binding`() = runTest {
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returnsMany listOf(
            ApiResult.Failure(4001, "check failed"), ApiResult.Success(Unit),
        )
        val scan = beginBinding { LocationRequestResult.Coordinates(fresh) }
        assertTrue(state.value is NfcSignInUiState.Error)
        assertFalse(scan.retryLocation { error("Business failure must not automatically resubmit") })
        orderDelegate.resetState()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()

        assertTrue(state.value is NfcSignInUiState.Success)
        assertNull(pending.value)
        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 2) { orders.checkOrder(1, "tag", "121.001", "31.001") }
        scan.clear()
    }

    @Test fun `binding failure does not acquire or submit a sign-in point`() = runTest {
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Failure(4001, "bind failed")
        var requests = 0
        val scan = beginBinding { requests++; LocationRequestResult.Coordinates(sample) }

        assertEquals(1, requests)
        assertTrue(state.value is NfcSignInUiState.Error)
        assertFalse(scan.retryLocation { error("Unknown binding outcome must not automatically replay") })
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        scan.clear()
    }

    @Test fun `cancelled binding response cannot start a later location acquisition`() = runTest {
        val binding = CompletableDeferred<ApiResult<Unit>>()
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { binding.await() }
        }
        var requests = 0
        val scan = beginBinding { requests++; LocationRequestResult.Coordinates(sample) }
        scan.clear()
        binding.complete(ApiResult.Success(Unit))
        runCurrent()

        assertEquals(1, requests)
        assertEquals(NfcSignInUiState.Initial, state.value)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
    }

    @Test fun `switching orders discards the old post-bind location and its binding marker`() = runTest {
        val oldLocation = CompletableDeferred<LocationRequestResult>()
        var requests = 0
        val scan = beginBinding {
            if (++requests == 1) LocationRequestResult.Coordinates(sample)
            else withContext(NonCancellable) { oldLocation.await() }
        }
        val next = OrderKey(2)
        scan.observeScanEvents(next, SignInMode.START_ORDER, null) { LocationRequestResult.Coordinates(fresh) }
        runCurrent()
        bus.send(AppEvent.TagScanned("next-tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        assertEquals(next, pending.value?.orderKey)
        oldLocation.complete(LocationRequestResult.Coordinates(sample))
        runCurrent()
        assertEquals(next, pending.value?.orderKey)
        coVerify(exactly = 0) { orders.checkOrder(1, any(), any(), any()) }

        scan.confirmLocationActivation(requireNotNull(pending.value)) { LocationRequestResult.Coordinates(fresh) }
        runCurrent()
        coVerify(exactly = 1) { orders.bindLocation(2, "next-tag", "121.001", "31.001") }
        coVerify(exactly = 1) { orders.checkOrder(2, "next-tag", "121.001", "31.001") }
        assertTrue(state.value is NfcSignInUiState.Success)
        scan.clear()
    }

    private suspend fun TestScope.beginBinding(request: suspend () -> LocationRequestResult): NfcScanWorkflowDelegate {
        val scan = NfcScanWorkflowDelegate(facade, request, bus, details, orders, backgroundScope,
            state, pending, MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired),
            orderDelegate, messages)
        scan.observeScanEvents(key, SignInMode.START_ORDER, null, request)
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
        assertNotNull(pending.value)
        scan.confirmLocationActivation(requireNotNull(pending.value), request)
        runCurrent()
        return scan
    }
}
