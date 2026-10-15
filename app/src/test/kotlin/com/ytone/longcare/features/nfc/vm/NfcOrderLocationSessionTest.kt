package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.event.*
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NfcOrderLocationSessionTest {
    private val bus = AppEventBus()
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val details = mockk<OrderDetailRepository>()
    private val facade = mockk<LocationFacade>()
    private val text = mockk<ResourceTextResolver> { every { text(any()) } returns "location error" }
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val pending = MutableStateFlow<PendingNfcData?>(null)
    private val messages = NfcUserMessages("network", "detail", "bind", "unavailable")
    private val key = OrderKey(1)
    private val bound = ServiceOrderInfoModel(userInfo = UserInfoM(lng = "121", lat = "31"))
    private val sample = LocationResult(31.0, 121.0, "test", 10f, "GCJ02", 5, 1,
        locationTime = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(), receivedElapsedRealtime = 1_000)
    private val orderDelegate = NfcOrderWorkflowDelegate(facade, text, mockk(relaxed = true), orders,
        details, mockk(relaxed = true), mockk(relaxed = true), state, messages)

    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000
        every { details.getCachedOrderInfo(any()) } returns bound
        every { facade.serviceOrderLocationFailure(any()) } returns null
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
    }
    @After fun cleanup() = unmockkAll()

    private fun TestScope.scan() = NfcScanWorkflowDelegate(facade, { LocationRequestResult.Coordinates(sample) },
        bus, details, orders, backgroundScope, state, pending, MutableStateFlow(ScanMode.SYSTEM_NFC),
        MutableStateFlow(ReaderUiState.NotRequired), orderDelegate, messages)

    @Test fun `customer lookup completes before SDK location starts`() = runTest {
        val response = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        every { details.getCachedOrderInfo(key) } returns null
        coEvery { details.getOrderInfo(key) } coAnswers { response.await() }
        var requests = 0
        val scan = scan()
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { requests++; LocationRequestResult.Coordinates(sample) }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertEquals(0, requests)
        response.complete(ApiResult.Success(bound)); runCurrent()
        assertEquals(1, requests)
        coVerify(exactly = 1) { orders.checkOrder(1, "tag", "121.0", "31.0") }
        scan.clear()
    }

    @Test fun `unbound customer waits for confirmation before any location acquisition`() = runTest {
        every { details.getCachedOrderInfo(key) } returns ServiceOrderInfoModel()
        var requests = 0
        val scan = scan()
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { requests++; LocationRequestResult.Coordinates(sample) }
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertNotNull(pending.value)
        assertEquals(0, requests)
        scan.confirmLocationActivation(requireNotNull(pending.value)) { requests++; LocationRequestResult.Coordinates(sample) }
        runCurrent()
        assertEquals(1, requests)
        coVerify(exactly = 1) { orders.bindLocation(1, "tag", "121.0", "31.0") }
        scan.clear()
    }

    @Test fun `late old order lookup success or failure cannot overwrite new order UI`() = runTest {
        val lateResults: List<ApiResult<ServiceOrderInfoModel>> = listOf(
            ApiResult.Success(bound), ApiResult.Failure(5001, "old order failed"),
            ApiResult.Exception(IllegalStateException("old request failed")),
        )
        for (lateResult in lateResults) {
            clearMocks(orders, answers = false)
            val old = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
            every { details.getCachedOrderInfo(key) } returns null
            coEvery { details.getOrderInfo(key) } coAnswers { withContext(NonCancellable) { old.await() } }
            var oldRequests = 0
            val scan = scan()
            scan.observeScanEvents(key, SignInMode.START_ORDER, null) { oldRequests++; LocationRequestResult.Coordinates(sample) }
            runCurrent()
            bus.send(AppEvent.TagScanned("old-tag", ScanSource.SYSTEM_NFC)); runCurrent()
            val next = OrderKey(2)
            scan.observeScanEvents(next, SignInMode.START_ORDER, null) { LocationRequestResult.Coordinates(sample) }
            runCurrent()
            bus.send(AppEvent.TagScanned("new-tag", ScanSource.SYSTEM_NFC)); runCurrent()
            old.complete(lateResult); runCurrent()
            assertEquals(0, oldRequests)
            coVerify(exactly = 0) { orders.checkOrder(1, any(), any(), any()) }
            coVerify(exactly = 1) { orders.checkOrder(2, "new-tag", "121.0", "31.0") }
            assertTrue(state.value is NfcSignInUiState.Success)
            scan.clear()
        }
    }

    @Test fun `blank inactive source and completed UI never initiate location`() = runTest {
        var requests = 0
        val scan = scan()
        scan.observeScanEvents(key, SignInMode.START_ORDER, null) { requests++; LocationRequestResult.Coordinates(sample) }
        runCurrent()
        bus.send(AppEvent.TagScanned(" ", ScanSource.SYSTEM_NFC)); runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.EXTERNAL_RFID)); runCurrent()
        state.value = NfcSignInUiState.Success()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        state.value = NfcSignInUiState.Error("error")
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertEquals(0, requests)
        scan.clear()
    }
}
