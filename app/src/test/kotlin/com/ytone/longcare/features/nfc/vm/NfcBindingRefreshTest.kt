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
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcBindingRefreshTest {
    private val bus = AppEventBus()
    private val facade = mockk<LocationFacade>()
    private val orders = mockk<OrderRepository>()
    private val details = mockk<OrderDetailRepository>()
    private val text = mockk<ResourceTextResolver> { every { text(any()) } returns "message" }
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val pending = MutableStateFlow<PendingNfcData?>(null)
    private val messages = NfcUserMessages("network", "detail", "bind", "unavailable")
    private val key = OrderKey(7, 3)
    private val unbound = ServiceOrderInfoModel(orderId = 7)
    private val bound = unbound.copy(userInfo = UserInfoM(lng = "121", lat = "31"))
    private var server = unbound
    private var locationCalls = 0
    private val orderDelegate = NfcOrderWorkflowDelegate(facade, text, mockk(relaxed = true), orders,
        details, nfcTestImageRepository(), mockk(relaxed = true), state, messages)

    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { facade.serviceOrderLocationFailure(any()) } returns null
        every { details.getCachedOrderInfo(key) } returns bound
        coEvery { details.getOrderInfo(key, true) } coAnswers { ApiResult.Success(server) }
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers { server = bound; ApiResult.Success(Unit) }
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
    }
    @After fun cleanup() = unmockkAll()

    @Test fun `cached bound state cannot bypass an unbound server and no location is requested before confirmation`() = runTest {
        val scan = attach()
        scanCard()
        assertNotNull(pending.value)
        assertEquals(0, locationCalls)
        verify(exactly = 0) { details.getCachedOrderInfo(any()) }
        scan.clear()
    }

    @Test fun `binding on another terminal while confirmation waits is rechecked instead of overwritten`() = runTest {
        val scan = attach()
        scanCard()
        server = bound
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        coVerify(exactly = 0) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 1) { orders.checkOrder(7, "tag", "121.0", "31.0") }
        assertEquals(1, locationCalls)
        assertTrue(state.value is NfcSignInUiState.Success)
        scan.clear()
    }

    @Test fun `lost bind response followed by another scan reconciles server state without rebinding`() = runTest {
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers {
            server = bound
            ApiResult.Exception(IOException("response lost"))
        }
        val scan = attach()
        scanCard()
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        assertTrue(state.value is NfcSignInUiState.Error)
        assertFalse(scan.retryLocation { error("Unknown binding cannot be automatically replayed") })
        orderDelegate.resetState()
        scanCard()
        assertTrue(state.value is NfcSignInUiState.Success)
        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 3) { details.getOrderInfo(key, true) }
        coVerify(exactly = 1) { orders.checkOrder(any(), any(), any(), any()) }
        scan.clear()
    }

    @Test fun `refresh failure at scan or confirmation stops mutation and location`() = runTest {
        val scan = attach()
        scanCard()
        coEvery { details.getOrderInfo(key, true) } returns ApiResult.Exception(IOException("offline"))
        scan.confirmLocationActivation(requireNotNull(pending.value))
        runCurrent()
        assertTrue(state.value is NfcSignInUiState.Error)
        assertEquals(0, locationCalls)
        coVerify(exactly = 0) { orders.bindLocation(any(), any(), any(), any()) }
        scan.clear()
        val next = attach()
        scanCard()
        assertNull(pending.value)
        assertTrue(state.value is NfcSignInUiState.Error)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        next.clear()
    }

    private fun TestScope.attach() = NfcScanWorkflowDelegate(facade, {
        locationCalls++
        LocationRequestResult.Coordinates(LocationResult(31.0, 121.0, "test", 10f, "GCJ02", 5, 1,
            locationTime = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(), receivedElapsedRealtime = 1_000L))
    }, bus, details, orders, backgroundScope, state, pending, MutableStateFlow(ScanMode.SYSTEM_NFC),
        MutableStateFlow(ReaderUiState.NotRequired), orderDelegate, messages).also {
        it.observeScanEvents(key, SignInMode.START_ORDER, null) {
            locationCalls++
            LocationRequestResult.Coordinates(LocationResult(31.0, 121.0, "test"))
        }
    }

    private suspend fun TestScope.scanCard() {
        runCurrent(); bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC)); runCurrent()
    }
}
