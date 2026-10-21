package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class NfcLocationFreshnessTest {
    private val facade = mockk<LocationFacade>()
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val messages = NfcUserMessages("network", "detail", "bind", "unavailable")
    private val text = mockk<ResourceTextResolver> { every { text(any()) } returns "location insufficient" }
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val delegate = NfcOrderWorkflowDelegate(facade, text, lifecycle, orders,
        details, mockk(relaxed = true), mockk(relaxed = true), state, messages)
    private val sample = LocationResult(31.0, 121.0, "test", 8f, "GCJ02", 5, 1,
        locationTime = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(), receivedElapsedRealtime = 1_000)
    private val key = OrderKey(1)

    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { facade.serviceOrderLocationFailure(any()) } answers {
            val location = firstArg<LocationResult>()
            ServiceOrderLocationQuality.rejection(
                location, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            )?.let { ServiceOrderLocationQuality.failure(location, it) }
        }
        coEvery { facade.acquireServiceOrderLocation() } returns LocationAcquisition.Success(sample)
        every { details.getCachedOrderInfo(any()) } returns ServiceOrderInfoModel(userInfo = UserInfoM(lng = "121", lat = "31"))
    }
    @After fun cleanup() = unmockkAll()

    @Test fun `insufficient start location never calls check order`() = runTest {
        every { facade.serviceOrderLocationFailure(sample) } returns LocationFailure.PRECISION
        delegate.startOrder(key, "tag", sample)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        assertEquals("location insufficient", (state.value as NfcSignInUiState.Error).message)
        every { facade.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        delegate.startOrder(key, "tag", sample)
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.0", "31.0") }
    }

    @Test fun `slow end precheck finishes before acquiring submission location`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } coAnswers { delay(16_000); ApiResult.Success(Unit) }
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
        val job = launch { delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList()) }
        runCurrent()
        coVerify(exactly = 0) { facade.acquireServiceOrderLocation() }
        advanceTimeBy(16_000)
        runCurrent()
        job.join()
        coVerify(exactly = 1) { facade.acquireServiceOrderLocation() }
        coVerify(exactly = 1) { orders.endOrder(any(), any(), any(), any(), any(), any(), "121.0", "31.0", 1) }
        verify(exactly = 1) { lifecycle.onOrderEnded(1) }
    }

    @Test fun `precision failure keeps end photos and projects and retry only reacquires location`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        coEvery { facade.acquireServiceOrderLocation() } returns LocationAcquisition.Failure(LocationFailure.PRECISION, sample.copy(accuracy = 2_000f))
        delegate.endOrder(key, "tag", listOf(7), listOf("before"), listOf("after"), listOf("middle"))
        assertTrue(delegate.hasPendingEndLocation)
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
        delegate.retryEndLocation { LocationRequestResult.Coordinates(sample) }
        coVerify(exactly = 1) { orders.checkEndOrder(1, listOf(7)) }
        coVerify(exactly = 1) { orders.endOrder(1, "tag", listOf(7), listOf("before"), listOf("middle"), listOf("after"), "121.0", "31.0", 1) }
        assertFalse(delegate.hasPendingEndLocation)
    }

    @Test fun `long end confirmation wait acquires fresh location only after confirmation`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Failure(3005, "confirm")
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList())
        val pending = (state.value as NfcSignInUiState.ShowConfirmDialog).endOrderParams
        coVerify(exactly = 0) { facade.acquireServiceOrderLocation() }
        delay(60_000)
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
        delegate.confirmEndOrder(pending)
        coVerify(exactly = 1) { facade.acquireServiceOrderLocation() }
        assertTrue(state.value is NfcSignInUiState.Success)
    }

    @Test fun `binding confirmation uses fresh coordinates instead of old preview`() = runTest {
        val pending = MutableStateFlow<PendingNfcData?>(PendingNfcData(key, SignInMode.START_ORDER, null, "tag"))
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        val scan = scan(AppEventBus(), pending) { LocationRequestResult.Coordinates(sample) }
        coVerify(exactly = 0) { orders.bindLocation(any(), any(), any(), any()) }
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        coVerify(exactly = 1) { orders.bindLocation(1, "tag", "121.0", "31.0") }
        assertNull(pending.value)
        scan.clear()
    }

    @Test fun `entering does not locate and repeated tags do not duplicate an in flight request`() = runTest {
        val bus = AppEventBus()
        var requests = 0
        val scan = scan(bus) { error("not used") }
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { requests++; awaitCancellation() }
        runCurrent()
        assertEquals(0, requests)
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertEquals(1, requests)
        scan.clear(); runCurrent()
        assertEquals(NfcSignInUiState.Initial, state.value)
    }

    @Test fun `permission denial and grant preserve tag without duplicate permission callbacks`() = runTest {
        val bus = AppEventBus()
        val scan = scan(bus) { error("not used") }
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { LocationRequestResult.PermissionRequired }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertEquals(NfcSignInUiState.Loading(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION), state.value)
        scan.clearPendingPermissionScan()
        assertFalse(scan.resumePendingPermissionScan { error("denied") })
        assertTrue(state.value is NfcSignInUiState.Error)
        // 拒绝后可直接重试原碰卡；重试再次弹出权限请求并等待授权。
        assertTrue(scan.retryLocation { LocationRequestResult.PermissionRequired }); runCurrent()
        var requests = 0
        assertTrue(scan.resumePendingPermissionScan { requests++; LocationRequestResult.Error("retryable") })
        assertFalse(scan.resumePendingPermissionScan { error("duplicate") })
        runCurrent()
        assertEquals(1, requests)
        assertEquals("retryable", (state.value as NfcSignInUiState.Error).message)
        scan.clear()
    }

    @Test fun `location retry reuses tag without a second NFC scan`() = runTest {
        val bus = AppEventBus()
        val scan = scan(bus) { error("not used") }
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { LocationRequestResult.Error("precision", reason = LocationFailure.PRECISION) }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        assertTrue(scan.retryLocation { LocationRequestResult.Coordinates(sample) })
        runCurrent()
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.0", "31.0") }
        scan.clear()
    }

    @Test fun `submission recheck failure preserves original tag for location retry`() = runTest {
        val bus = AppEventBus()
        val scan = scan(bus) { error("not used") }
        every { facade.serviceOrderLocationFailure(sample) } returns LocationFailure.PERMISSION
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { LocationRequestResult.Coordinates(sample) }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertTrue(state.value is NfcSignInUiState.Error)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        every { facade.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        assertTrue(scan.retryLocation { LocationRequestResult.Coordinates(sample) }); runCurrent()
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.0", "31.0") }
        scan.clear()
    }

    @Test fun `start keeps coordinate order and full double precision`() = runTest {
        val precise = sample.copy(latitude = 31.23456789012345, longitude = 121.98765432109876)
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        delegate.startOrder(key, "tag", precise)
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", precise.longitude.toString(), precise.latitude.toString()) }
    }

    @Test fun `start submits fresh GPS coordinates despite UTC and phone wall clock differences`() = runTest {
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        for ((index, offset) in listOf(5_000L, -60_000L).withIndex()) {
            val receivedAt = System.currentTimeMillis()
            val location = sample.copy(
                provider = "gps", locationType = 1, sourceElapsedRealtime = 1_000L,
                receivedAt = receivedAt, locationTime = receivedAt + offset,
                latitude = 31.23456789012345, longitude = 121.98765432109876,
            )
            val orderKey = OrderKey(index + 1L)

            assertTrue(delegate.startOrder(orderKey, "tag", location))

            coVerify(exactly = 1) {
                orders.checkOrder(orderKey.orderId, "tag", location.longitude.toString(), location.latitude.toString())
            }
        }
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
    }

    @Test fun `end submits fresh GPS coordinates and waits for API success before ending lifecycle`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        for ((index, offset) in listOf(5_000L, -60_000L).withIndex()) {
            val receivedAt = System.currentTimeMillis()
            val location = sample.copy(
                provider = "gps", locationType = 1, sourceElapsedRealtime = 1_000L,
                receivedAt = receivedAt, locationTime = receivedAt + offset,
                latitude = 31.23456789012345, longitude = 121.98765432109876,
            )
            val orderKey = OrderKey(index + 1L)
            val response = CompletableDeferred<ApiResult<EndOrderResultModel>>()
            coEvery { facade.acquireServiceOrderLocation() } returns LocationAcquisition.Success(location)
            coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers { response.await() }
            val job = launch { delegate.endOrder(orderKey, "tag", listOf(7), listOf("before"), listOf("after"), listOf("middle")) }
            runCurrent()

            coVerify(exactly = 1) {
                orders.endOrder(orderKey.orderId, "tag", listOf(7), listOf("before"), listOf("middle"), listOf("after"),
                    location.longitude.toString(), location.latitude.toString(), 1)
            }
            verify(exactly = 0) { lifecycle.onOrderEnded(orderKey.orderId) }

            response.complete(ApiResult.Success(EndOrderResultModel(60)))
            job.join()

            verify(exactly = 1) { lifecycle.onOrderEnded(orderKey.orderId) }
            assertTrue(state.value is NfcSignInUiState.Success)
        }
    }

    @Test fun `failed end submission with fresh GPS leaves the order lifecycle active`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Failure(4001, "not ended")
        for ((index, offset) in listOf(5_000L, -60_000L).withIndex()) {
            val receivedAt = System.currentTimeMillis()
            val location = sample.copy(
                provider = "gps", locationType = 1, sourceElapsedRealtime = 1_000L,
                receivedAt = receivedAt, locationTime = receivedAt + offset,
            )
            coEvery { facade.acquireServiceOrderLocation() } returns LocationAcquisition.Success(location)

            delegate.endOrder(OrderKey(index + 1L), "tag", emptyList(), emptyList(), emptyList())

            assertEquals("not ended", (state.value as NfcSignInUiState.Error).message)
        }
        coVerify(exactly = 2) { orders.endOrder(any(), any(), any(), any(), any(), any(), "121.0", "31.0", 1) }
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
    }

    private fun CoroutineScope.scan(bus: AppEventBus, pending: MutableStateFlow<PendingNfcData?> = MutableStateFlow(null), request: suspend () -> LocationRequestResult) =
        NfcScanWorkflowDelegate(facade, request, bus, details, orders, this, state, pending,
            MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired), delegate, messages)
}
