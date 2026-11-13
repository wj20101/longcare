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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcPageSessionTest {
    private val bus = AppEventBus()
    private val orders = mockk<OrderRepository>()
    private lateinit var vm: NfcWorkflowViewModel
    private val point = LocationRequestResult.Coordinates(LocationResult(31.0, 121.0, "test"))

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        val details = mockk<OrderDetailRepository> {
            coEvery { getOrderInfo(any(), true) } returns ApiResult.Success(ServiceOrderInfoModel(userInfo = UserInfoM(lng = "121", lat = "31")))
        }
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        vm = NfcWorkflowViewModel(orders, mockk(relaxed = true), bus, mockk(relaxed = true),
            mockk<LocationFacade> { every { serviceOrderLocationFailure(any()) } returns null },
            details, nfcTestImageRepository(), mockk(relaxed = true),
            mockk { every { isNfcSupported() } returns true },
            mockk<ResourceTextResolver> { every { text(any()) } returns "message" })
    }
    @After fun cleanup() { vm.stopScanRequests(); Dispatchers.resetMain(); unmockkAll() }

    @Test fun `late old page event cannot use its tag on the new order`() = runTest {
        observe(OrderKey(1), "old")
        observe(OrderKey(2, 9), "new")
        runCurrent()
        bus.send(AppEvent.TagScanned("old-tag", ScanSource.SYSTEM_NFC, "old")); runCurrent()
        bus.send(AppEvent.TagScanned("unscoped-tag", ScanSource.SYSTEM_NFC)); runCurrent()
        assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        bus.send(AppEvent.TagScanned("new-tag", ScanSource.SYSTEM_NFC, "new")); runCurrent()
        coVerify(exactly = 1) { orders.checkOrder(2, "new-tag", "121.0", "31.0") }
    }

    @Test fun `old disposal cannot detach the new page and same card remains usable after actual exit`() = runTest {
        observe(OrderKey(1), "old")
        vm.stopScanRequests()
        observe(OrderKey(1), "new")
        vm.stopScanRequests(scanSessionId = "old")
        runCurrent()
        bus.send(AppEvent.TagScanned("same-tag", ScanSource.SYSTEM_NFC, "old")); runCurrent()
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        bus.send(AppEvent.TagScanned("same-tag", ScanSource.SYSTEM_NFC, "new")); runCurrent()
        coVerify(exactly = 1) { orders.checkOrder(1, "same-tag", "121.0", "31.0") }
    }

    @Test fun `configuration attachment accepts its new session but not old or external source events`() = runTest {
        observe(OrderKey(1), "old")
        vm.stopScanRequests(preserveWorkflow = true)
        observe(OrderKey(1), "new")
        runCurrent()
        bus.send(AppEvent.TagScanned("same-tag", ScanSource.SYSTEM_NFC, "old"))
        bus.send(AppEvent.TagScanned("same-tag", ScanSource.EXTERNAL_RFID, "new"))
        runCurrent()
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        bus.send(AppEvent.TagScanned("same-tag", ScanSource.SYSTEM_NFC, "new")); runCurrent()
        assertTrue(vm.uiState.value is NfcSignInUiState.Success)
        coVerify(exactly = 1) { orders.checkOrder(any(), any(), any(), any()) }
    }

    private fun observe(key: OrderKey, sessionId: String) {
        vm.observeScanEvents(key, SignInMode.START_ORDER, null, sessionId) { point }
    }
}
