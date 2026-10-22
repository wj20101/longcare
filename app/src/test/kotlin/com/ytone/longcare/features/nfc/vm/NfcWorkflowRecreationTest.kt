package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.R
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.EndOrderResultModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.UserInfoM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcWorkflowRecreationTest {
    private val bus = AppEventBus()
    private val orders = mockk<OrderRepository>()
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val facade = mockk<LocationFacade>()
    private val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
    private val countdown = mockk<ServiceCountdownSystemGateway>(relaxed = true)
    private val key = OrderKey(1)
    private val endInfo = EndOderInfo(projectIdList = listOf(7), beginImgList = listOf("before"),
        centerImgList = listOf("middle"), endImgList = listOf("after"))
    private val sample = LocationResult(31.0, 121.0, "test", 10f, "GCJ02", 5, 1,
        locationTime = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(),
        receivedElapsedRealtime = 1_000L)
    private lateinit var vm: NfcWorkflowViewModel

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { facade.serviceOrderLocationFailure(any()) } returns null
        every { details.getCachedOrderInfo(any()) } returns
            ServiceOrderInfoModel(userInfo = UserInfoM(lng = "121", lat = "31"))
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.bindLocation(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Failure(3005, "confirm")
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            ApiResult.Success(EndOrderResultModel(60))
        val text = mockk<ResourceTextResolver> {
            every { text(any()) } returns "message"
            every { text(R.string.nfc_workflow_interrupted) } returns "location interrupted"
            every { text(R.string.nfc_submission_interrupted) } returns "submission unknown"
        }
        vm = NfcWorkflowViewModel(orders, lifecycle, bus, mockk(relaxed = true), facade,
            details, mockk(relaxed = true), countdown,
            mockk { every { isNfcSupported() } returns true }, text)
    }

    @After fun teardown() {
        vm.stopScanRequests()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun observe(mode: SignInMode = SignInMode.START_ORDER, order: OrderKey = key,
                        info: EndOderInfo? = if (mode == SignInMode.END_ORDER) endInfo else null,
                        request: suspend () -> LocationRequestResult = { LocationRequestResult.Coordinates(sample) }) {
        vm.observeScanEvents(order, mode, info, request)
    }

    private suspend fun TestScope.scan() {
        runCurrent()
        bus.send(AppEvent.TagScanned("tag", ScanSource.SYSTEM_NFC))
        runCurrent()
    }

    @Test fun `same workflow rebind retains confirmed success and never checks again`() = runTest {
        observe()
        scan()
        val success = vm.uiState.value
        assertTrue(success is NfcSignInUiState.Success)
        vm.stopScanRequests(preserveWorkflow = true)
        observe(request = { error("confirmed workflow must not request location again") })
        scan()
        assertEquals(success, vm.uiState.value)
        coVerify(exactly = 1) { orders.checkOrder(any(), any(), any(), any()) }
    }

    @Test fun `3005 confirmation and confirmed end success survive configuration replacement`() = runTest {
        observe(SignInMode.END_ORDER)
        scan()
        val confirmation = vm.uiState.value as NfcSignInUiState.ShowConfirmDialog
        vm.stopScanRequests(preserveWorkflow = true)
        observe(SignInMode.END_ORDER, info = endInfo.copy())
        runCurrent()
        assertEquals(confirmation, vm.uiState.value)
        vm.confirmEndOrder(confirmation.endOrderParams) { LocationRequestResult.Coordinates(sample) }
        runCurrent()
        val success = vm.uiState.value
        assertEquals(NfcSignInUiState.Success(EndOrderSuccessData(60)), success)
        vm.stopScanRequests(preserveWorkflow = true)
        observe(SignInMode.END_ORDER)
        scan()
        assertEquals(success, vm.uiState.value)
        coVerify(exactly = 1) { orders.checkEndOrder(any(), any()) }
        coVerify(exactly = 1) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `permission result before new launch effect uses new callback exactly once`() = runTest {
        var oldRequests = 0
        observe(request = { oldRequests++; LocationRequestResult.PermissionRequired })
        scan()
        vm.stopScanRequests(preserveWorkflow = true)
        var newRequests = 0
        val result = CompletableDeferred<LocationRequestResult>()
        assertTrue(vm.resumePendingPermissionScan { newRequests++; result.await() })
        runCurrent()
        observe(request = { error("new event collector must not replay pending permission") })
        runCurrent()
        result.complete(LocationRequestResult.Coordinates(sample))
        runCurrent()
        assertFalse(vm.resumePendingPermissionScan { error("duplicate permission result") })
        assertEquals(1, oldRequests)
        assertEquals(1, newRequests)
        assertTrue(vm.uiState.value is NfcSignInUiState.Success)
        coVerify(exactly = 1) { orders.checkOrder(any(), any(), any(), any()) }
    }

    @Test fun `pending binding and already confirmed binding survive without duplicate binding`() = runTest {
        every { details.getCachedOrderInfo(key) } returns ServiceOrderInfoModel()
        observe()
        scan()
        val pending = requireNotNull(vm.pendingNfcData.value)
        vm.stopScanRequests(preserveWorkflow = true)
        observe()
        runCurrent()
        assertEquals(pending, vm.pendingNfcData.value)
        var requests = 0
        vm.confirmLocationActivation(pending) {
            requests++
            if (requests == 1) LocationRequestResult.Coordinates(sample)
            else LocationRequestResult.Error("retry fresh location")
        }
        runCurrent()
        assertTrue(vm.uiState.value is NfcSignInUiState.Error)
        vm.stopScanRequests(preserveWorkflow = true)
        observe()
        runCurrent()
        vm.retryLocation { LocationRequestResult.Coordinates(sample) }
        runCurrent()
        assertTrue(vm.uiState.value is NfcSignInUiState.Success)
        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 1) { orders.checkOrder(any(), any(), any(), any()) }
    }

    @Test fun `in flight old activity location is canceled and becomes an explicit fresh retry`() = runTest {
        var canceled = false
        observe(request = { try { awaitCancellation() } finally { canceled = true } })
        scan()
        vm.stopScanRequests(preserveWorkflow = true)
        observe()
        runCurrent()
        assertTrue(canceled)
        assertEquals("location interrupted", (vm.uiState.value as NfcSignInUiState.Error).message)
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
        vm.retryLocation { LocationRequestResult.Coordinates(sample) }
        runCurrent()
        assertTrue(vm.uiState.value is NfcSignInUiState.Success)
    }

    @Test fun `end location permission parameters and new callback survive before rebind`() = runTest {
        observe(SignInMode.END_ORDER)
        scan()
        val params = (vm.uiState.value as NfcSignInUiState.ShowConfirmDialog).endOrderParams
        vm.confirmEndOrder(params) { LocationRequestResult.PermissionRequired }
        runCurrent()
        vm.stopScanRequests(preserveWorkflow = true)
        val result = CompletableDeferred<LocationRequestResult>()
        assertTrue(vm.resumePendingPermissionScan { result.await() })
        runCurrent()
        observe(SignInMode.END_ORDER)
        runCurrent()
        result.complete(LocationRequestResult.Coordinates(sample))
        runCurrent()
        assertTrue(vm.uiState.value is NfcSignInUiState.Success)
        coVerify(exactly = 1) {
            orders.endOrder(1, "tag", listOf(7), listOf("before"), listOf("middle"), listOf("after"),
                "121.0", "31.0", 1)
        }
    }

    @Test fun `unknown submitted end result is not replayed and late response cannot restore success`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        val response = CompletableDeferred<ApiResult<EndOrderResultModel>>()
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        observe(SignInMode.END_ORDER)
        scan()
        vm.stopScanRequests(preserveWorkflow = true)
        observe(SignInMode.END_ORDER)
        runCurrent()
        assertEquals("submission unknown", (vm.uiState.value as NfcSignInUiState.Error).message)
        vm.retryLocation { error("unknown submission must not be replayed") }
        runCurrent()
        response.complete(ApiResult.Success(EndOrderResultModel(60)))
        runCurrent()
        assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
        coVerify(exactly = 1) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `real exit clears permission and pending binding rather than reviving them`() = runTest {
        observe(request = { LocationRequestResult.PermissionRequired })
        scan()
        vm.stopScanRequests()
        assertFalse(vm.resumePendingPermissionScan { error("exited") })
        observe()
        runCurrent()
        assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
        assertFalse(vm.resumePendingPermissionScan { error("stale permission") })
        every { details.getCachedOrderInfo(key) } returns ServiceOrderInfoModel()
        scan()
        val pending = requireNotNull(vm.pendingNfcData.value)
        vm.stopScanRequests()
        observe()
        runCurrent()
        vm.confirmLocationActivation(pending) { error("stale binding") }
        assertNull(vm.pendingNfcData.value)
        coVerify(exactly = 0) { orders.bindLocation(any(), any(), any(), any()) }
    }

    @Test fun `unknown binding submission is not treated as an interrupted location retry`() = runTest {
        every { details.getCachedOrderInfo(key) } returns ServiceOrderInfoModel()
        val response = CompletableDeferred<ApiResult<Unit>>()
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        observe()
        scan()
        vm.confirmLocationActivation(requireNotNull(vm.pendingNfcData.value)) {
            LocationRequestResult.Coordinates(sample)
        }
        runCurrent()
        vm.stopScanRequests(preserveWorkflow = true)
        observe()
        runCurrent()
        assertEquals("submission unknown", (vm.uiState.value as NfcSignInUiState.Error).message)
        vm.retryLocation { error("binding must not be replayed") }
        response.complete(ApiResult.Success(Unit))
        runCurrent()
        assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
        coVerify(exactly = 1) { orders.bindLocation(any(), any(), any(), any()) }
        coVerify(exactly = 0) { orders.checkOrder(any(), any(), any(), any()) }
    }

    @Test fun `order mode or end parameters change clears retained confirmation and rejects old action`() = runTest {
        val changes = listOf(
            NfcWorkflowIdentity(OrderKey(2), SignInMode.END_ORDER, endInfo),
            NfcWorkflowIdentity(key, SignInMode.START_ORDER, null),
            NfcWorkflowIdentity(key, SignInMode.END_ORDER, endInfo.copy(endType = 2)),
        )
        for (next in changes) {
            observe(SignInMode.END_ORDER)
            scan()
            val params = (vm.uiState.value as NfcSignInUiState.ShowConfirmDialog).endOrderParams
            vm.stopScanRequests(preserveWorkflow = true)
            observe(next.signInMode, next.orderKey, next.endOderInfo)
            runCurrent()
            assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
            vm.confirmEndOrder(params) { error("old workflow action") }
            runCurrent()
            vm.stopScanRequests()
        }
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `non cancellable confirmed cleanup completes but cannot overwrite a new workflow`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        val cleanup = CompletableDeferred<Unit>()
        coEvery { details.endLocalService(key) } coAnswers { cleanup.await() }
        observe(SignInMode.END_ORDER)
        scan()
        vm.stopScanRequests()
        observe(order = OrderKey(2))
        runCurrent()
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(NfcSignInUiState.Initial, vm.uiState.value)
        verify(exactly = 1) { lifecycle.onOrderEnded(1) }
        coVerify(exactly = 1) { details.endLocalService(key) }
    }
}
