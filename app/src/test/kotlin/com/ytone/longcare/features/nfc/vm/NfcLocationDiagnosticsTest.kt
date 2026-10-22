package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.EndOrderResultModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NfcLocationDiagnosticsTest {
    private val reports = mutableListOf<DiagnosticException>()
    private val key = OrderKey(42, 7)
    private val now = System.currentTimeMillis()
    private val sample = LocationResult(31.13812345678, 121.98765432109876, "network", accuracy = 18f,
        coordType = "GCJ02", locationType = 5, trustedLevel = 1, locationTime = now - 100,
        receivedAt = now, receivedElapsedRealtime = 1_000)

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "123"
        every { CrashReportGateway.postCaughtException(capture(reports)) } just Runs
        every { CrashReportGateway.recordBreadcrumb(any(), any()) } just Runs
    }
    @After fun cleanup() { unmockkAll() }

    @Test fun `both modes independently upload complete AMap success data with exact coordinates`() = runTest {
        for (mode in SignInMode.entries) {
            val result = requestNfcLocation(key, mode, "scan", "unavailable") { LocationRequestResult.Coordinates(sample) }
            assertEquals(LocationRequestResult.Coordinates(sample), result)
            val fields = reports.last().fields
            assertEquals("INFO", fields["level"])
            assertEquals(mode.name, fields["signInMode"])
            assertEquals("123", fields["userId"])
            assertEquals("42", fields["orderId"])
            assertEquals(sample.longitude.toString(), fields["longitude"])
            assertEquals(sample.latitude.toString(), fields["latitude"])
            assertEquals("GCJ02", fields["coordType"])
            assertEquals("18.0", fields["accuracy"])
            assertEquals("5", fields["locationType"])
            assertEquals("1", fields["trustedLevel"])
            assertEquals(sample.locationTime.toString(), fields["locationTime"])
            assertEquals("false", fields["isMock"])
            assertEquals("false", fields["isLastLocation"])
            assertTrue(reports.last().message!!.length <= 950)
        }
        assertEquals(2, reports.size)
    }

    @Test fun `SDK failure preserves reason code and error info without duplicate fallback reporting`() = runTest {
        val failed = sample.copy(errorCode = 4, errorInfo = "network unavailable token=secret")
        val result = requestNfcLocation(key, SignInMode.END_ORDER, "scan", "unavailable") {
            LocationRequestResult.Error("定位失败", reason = LocationFailure.NETWORK, location = failed)
        } as LocationRequestResult.Error
        assertTrue(result.buglyReported)
        assertEquals("NETWORK", reports.single().fields["errorCode"])
        assertEquals("4", reports.single().fields["amapErrorCode"])
        assertTrue(reports.single().fields.getValue("amapErrorInfo").contains("network unavailable"))
        assertFalse(reports.single().message!!.contains("secret"))
    }

    @Test fun `permission and cancellation do not upload errors or invent coordinates`() = runTest {
        assertEquals(LocationRequestResult.PermissionRequired,
            requestNfcLocation(key, SignInMode.START_ORDER, "scan", "unavailable") { LocationRequestResult.PermissionRequired })
        val cancellation = CancellationException("Location request cancelled")
        try {
            requestNfcLocation(key, SignInMode.END_ORDER, "scan", "unavailable") { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertTrue(reports.isEmpty())
    }

    @Test fun `request snapshots raw identity across a suspended callback`() = runTest {
        requestNfcLocation(key, SignInMode.START_ORDER, "scan", "unavailable") {
            every { CrashReportGateway.userId } returns "456"
            LocationRequestResult.Coordinates(sample)
        }
        assertEquals("123", reports.single().fields["userId"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `binding failure retains submission identity and full location evidence`() = runTest {
        val facade = mockk<LocationFacade> { every { serviceOrderLocationFailure(sample) } returns null }
        val orders = mockk<OrderRepository>()
        val data = PendingNfcData(key, SignInMode.START_ORDER, null, "tag")
        val pending = MutableStateFlow<PendingNfcData?>(data)
        val orderDelegate = mockk<NfcOrderWorkflowDelegate>(relaxed = true)
        coEvery { orders.bindLocation(any(), any(), any(), any()) } coAnswers {
            every { CrashReportGateway.userId } returns "456"
            ApiResult.Failure(4001, "绑定失败")
        }
        val scan = NfcScanWorkflowDelegate(facade, { LocationRequestResult.Coordinates(sample) }, AppEventBus(),
            mockk(), orders, backgroundScope, MutableStateFlow(NfcSignInUiState.Initial), pending,
            MutableStateFlow(ScanMode.SYSTEM_NFC), MutableStateFlow(ReaderUiState.NotRequired), orderDelegate,
            NfcUserMessages("network", "detail", "bind", "unavailable"))
        scan.confirmLocationActivation(data)
        runCurrent()
        val fields = reports.single { it.fields["eventCode"] == "bind_location_failure" }.fields
        assertEquals("123", fields["userId"])
        assertEquals(sample.latitude.toString(), fields["latitude"])
        assertEquals(sample.longitude.toString(), fields["longitude"])
        assertEquals("18.0", fields["accuracy"])
        assertEquals("4001", fields["failureCode"])
        coVerify(exactly = 0) { orderDelegate.startOrder(any(), any(), any()) }
        assertNull(pending.value)
        scan.clear()
    }

    @Test fun `server distance rejection includes exact submitted point distinct from sign in`() = runTest {
        val endSample = sample.copy(latitude = 31.13899999999, longitude = 121.98799999999)
        val facade = mockk<LocationFacade> { every { serviceOrderLocationFailure(any()) } returns null }
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        var ageWhenSubmitted = 0L
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            ageWhenSubmitted = System.currentTimeMillis() - endSample.locationTime
            // Network latency must not be presented as sample age at submission.
            withContext(Dispatchers.Default) { delay(100) }
            ApiResult.Failure(4001, "超出签到范围")
        }
        val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
        val delegate = NfcOrderWorkflowDelegate(facade, mockk(), mockk(), orders,
            mockk(), mockk(), mockk(), state, NfcUserMessages("network", "detail", "bind", "unavailable"))
        delegate.startOrder(key, "tag", sample)
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = { LocationRequestResult.Coordinates(endSample) })
        coVerify(exactly = 1) { orders.checkOrder(key.orderId, "tag", sample.longitude.toString(), sample.latitude.toString()) }
        coVerify(exactly = 1) {
            orders.endOrder(key.orderId, "tag", emptyList(), emptyList(), emptyList(), emptyList(),
                endSample.longitude.toString(), endSample.latitude.toString(), 1)
        }
        assertEquals("超出签到范围", (state.value as NfcSignInUiState.Error).message)
        val start = reports.single { it.fields["eventCode"] == "nfc_location_submit_success" }.fields
        assertEquals(sample.latitude.toString(), start["latitude"])
        val failure = reports.single { it.fields["eventCode"] == "nfc_end_order_result" }
        val fields = failure.fields
        assertEquals("4001", fields["failureCode"])
        assertEquals("超出签到范围", fields["failureMessage"])
        assertEquals(endSample.latitude.toString(), fields["latitude"])
        assertEquals(endSample.longitude.toString(), fields["longitude"])
        assertEquals("GCJ02", fields["coordType"])
        assertEquals("18.0", fields["accuracy"])
        assertEquals("END_ORDER", fields["signInMode"])
        assertTrue("Checkout summary must retain its original time evidence", fields.getValue("time").contains("issue=none"))
        assertTrue("Sample age must be captured before sending the request", fields.getValue("sampleAgeMs").toLong() <= ageWhenSubmitted)
        assertTrue(failure.message!!.length <= 950)
    }

    @Test fun `checkout uploads one outcome with stage timings and original user across account switch`() = runTest {
        var elapsed = 1_000L
        every { SystemClock.elapsedRealtime() } answers { elapsed }
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } coAnswers {
            elapsed += 2_000
            every { CrashReportGateway.userId } returns "456"
            ApiResult.Success(Unit)
        }
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            elapsed += 4_000
            ApiResult.Success(EndOrderResultModel(60))
        }
        checkout(orders).endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = {
            elapsed += 30_000
            LocationRequestResult.Coordinates(sample)
        })
        val fields = reports.single().fields
        assertEquals("nfc_end_order_result", fields["eventCode"])
        assertEquals("123", fields["userId"])
        assertEquals("success", fields["outcome"])
        assertEquals("completion", fields["stage"])
        assertEquals("36000", fields["durationMs"])
        assertEquals("2000", fields["checkMs"])
        assertEquals("30000", fields["locationMs"])
        assertEquals("4000", fields["submitMs"])
        assertEquals("0", fields["completionMs"])
        assertEquals(sample.latitude.toString(), fields["latitude"])
        assertEquals(sample.longitude.toString(), fields["longitude"])
        assertEquals("18.0", fields["accuracy"])
        assertNotNull(fields["attemptId"])
        assertTrue(reports.single().message!!.length <= 950)
    }

    @Test fun `location failure and retry each upload one result with distinct attempt identities`() = runTest {
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
        val delegate = checkout(orders)
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = {
            LocationRequestResult.Error("failed", reason = LocationFailure.NETWORK, location = sample.copy(errorCode = 4))
        })
        assertTrue(delegate.hasPendingEndLocation)
        delegate.retryEndLocation { LocationRequestResult.Coordinates(sample) }
        val first = reports[0].fields
        val retry = reports[1].fields
        assertEquals(2, reports.size)
        assertEquals("location_failure", first["outcome"])
        assertEquals("NETWORK", first["reason"])
        assertEquals("4", first["amapErrorCode"])
        assertEquals("location", first["stage"])
        assertFalse(first.containsKey("submitMs"))
        assertEquals("success", retry["outcome"])
        assertEquals("location_retry", retry["entry"])
        assertFalse(retry.containsKey("checkMs"))
        assertNotEquals(first["attemptId"], retry["attemptId"])
        coVerify(exactly = 1) { orders.checkEndOrder(any(), any()) }
    }

    @Test fun `checkout precheck failure is distinct from location or submit failure`() = runTest {
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Failure(4002, "cannot checkout")
        checkout(orders).endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = { error("must not locate") })
        val fields = reports.single().fields
        assertEquals("check", fields["stage"])
        assertEquals("api_failure", fields["outcome"])
        assertEquals("4002", fields["failureCode"])
        assertFalse(fields.containsKey("locationMs"))
        assertFalse(fields.containsKey("submitMs"))
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `checkout cancellation propagates without remote failure and clears no retry data`() = runTest {
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        val delegate = checkout(orders)
        val cancellation = CancellationException("user left")
        try {
            delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = { throw cancellation })
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertTrue(reports.isEmpty())
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `checkout permission wait is an info outcome with no API submit`() = runTest {
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        val delegate = checkout(orders)
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = { LocationRequestResult.PermissionRequired })
        val fields = reports.single().fields
        assertEquals("INFO", fields["level"])
        assertEquals("permission_required", fields["outcome"])
        assertEquals("location", fields["stage"])
        assertTrue(delegate.hasPendingEndLocation)
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `confirmation wait is not counted as a slow SDK request or repeated precheck`() = runTest {
        var elapsed = 1_000L
        every { SystemClock.elapsedRealtime() } answers { elapsed }
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } coAnswers {
            elapsed += 100
            ApiResult.Failure(3005, "confirmation")
        }
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
        val delegate = checkout(orders)
        delegate.endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = { error("must wait for user") })
        elapsed += 600_000
        delegate.confirmEndOrder(EndOrderParams(key, "tag", emptyList(), emptyList(), emptyList(), emptyList(), 1), requestLocation = {
            elapsed += 2_000
            LocationRequestResult.Coordinates(sample)
        })
        assertEquals(2, reports.size)
        val check = reports[0].fields
        val confirmation = reports[1].fields
        assertEquals("confirmation_required", check["outcome"])
        assertEquals("INFO", check["level"])
        assertEquals("100", check["durationMs"])
        assertEquals("confirmation", confirmation["entry"])
        assertEquals("2000", confirmation["durationMs"])
        assertEquals("2000", confirmation["locationMs"])
        assertNotEquals(check["attemptId"], confirmation["attemptId"])
        coVerify(exactly = 1) { orders.checkEndOrder(any(), any()) }
    }

    @Test fun `unexpected repository exception is reported once and still propagates`() = runTest {
        val orders = mockk<OrderRepository>()
        val failure = IllegalStateException("repository failed")
        coEvery { orders.checkEndOrder(any(), any()) } throws failure
        try {
            checkout(orders).endOrder(key, "tag", emptyList(), emptyList(), emptyList())
            fail("Unexpected errors must keep existing propagation")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals("exception", reports.single().fields["outcome"])
        assertEquals("check", reports.single().fields["stage"])
        assertEquals(IllegalStateException::class.java.name, reports.single().fields["errorType"])
    }

    @Test fun `unexpected location exception keeps original evidence in the single checkout result`() = runTest {
        val orders = mockk<OrderRepository>()
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        checkout(orders).endOrder(key, "tag", emptyList(), emptyList(), emptyList(), requestLocation = {
            throw IllegalStateException("SDK bridge unavailable")
        })
        val fields = reports.single().fields
        assertEquals("location_failure", fields["outcome"])
        assertEquals("UNAVAILABLE", fields["reason"])
        assertEquals("location", fields["stage"])
        assertEquals(IllegalStateException::class.java.name, fields["errorType"])
        coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun checkout(orders: OrderRepository): NfcOrderWorkflowDelegate = NfcOrderWorkflowDelegate(
        mockk { every { serviceOrderLocationFailure(any()) } returns null },
        mockk(relaxed = true), mockk(relaxed = true), orders, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), MutableStateFlow(NfcSignInUiState.Initial),
        NfcUserMessages("network", "detail", "bind", "unavailable"),
    )
}
