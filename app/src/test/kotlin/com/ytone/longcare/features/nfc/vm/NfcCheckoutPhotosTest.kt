package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcCheckoutPhotosTest {
    private val orders = mockk<OrderRepository>()
    private val images = mockk<OrderImageRepository>(relaxed = true)
    private val facade = mockk<LocationFacade>()
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    private val key = OrderKey(7, 3)
    private var rows = uploadedNfcPhotos(7)
    private val point = LocationResult(31.0, 121.0, "test")
    private val delegate = NfcOrderWorkflowDelegate(facade,
        mockk<ResourceTextResolver> { every { text(any()) } returns "photos invalid" },
        mockk(relaxed = true), orders, mockk(relaxed = true), images, mockk(relaxed = true), state,
        NfcUserMessages("network", "detail", "bind", "unavailable"))

    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { facade.serviceOrderLocationFailure(any()) } returns null
        coEvery { images.getImagesByOrderId(key) } coAnswers { rows }
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Success(Unit)
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(EndOrderResultModel(60))
    }
    @After fun cleanup() = unmockkAll()

    @Test fun `final checkout arrays come from current uploaded rows rather than route parameters`() = runTest {
        checkout()
        coVerify(exactly = 1) { orders.endOrder(7, "tag", listOf(5), listOf("before"), listOf("middle"), listOf("after"), "121.0", "31.0", 1) }
        assertTrue(state.value is NfcSignInUiState.Success)
    }

    @Test fun `failed pending or missing photos block before location and never submit empty arrays`() = runTest {
        rows = rows.map { it.copy(uploadStatus = ImageUploadStatus.PENDING.value, cloudKey = null) }
        delegate.endOrder(key, "tag", listOf(5), listOf("old"), listOf("old"), requestLocation = { error("Incomplete photos must stop first") })
        assertTrue(state.value is NfcSignInUiState.Error)
        assertFalse(delegate.hasPendingEndLocation)
        noSubmission()
    }

    @Test fun `deletion during location wait is checked again before submission`() = runTest {
        val location = CompletableDeferred<LocationRequestResult>()
        val job = launch { delegate.endOrder(key, "tag", listOf(5), emptyList(), emptyList(), requestLocation = { location.await() }) }
        runCurrent()
        rows = emptyList()
        location.complete(LocationRequestResult.Coordinates(point))
        job.join()
        assertTrue(state.value is NfcSignInUiState.Error)
        noSubmission()
    }

    @Test fun `confirmation wait uses the current replacement photos and preserves project and end type`() = runTest {
        coEvery { orders.checkEndOrder(any(), any()) } returns ApiResult.Failure(3005, "confirm")
        delegate.endOrder(key, "tag", listOf(5), listOf("old"), listOf("old"), endType = 2)
        val params = (state.value as NfcSignInUiState.ShowConfirmDialog).endOrderParams
        rows = rows.map { it.copy(cloudKey = "new-${it.cloudKey}") }
        delegate.confirmEndOrder(params) { LocationRequestResult.Coordinates(point) }
        coVerify(exactly = 1) { orders.endOrder(7, "tag", listOf(5), listOf("new-before"), listOf("new-middle"), listOf("new-after"), "121.0", "31.0", 2) }
    }

    @Test fun `permission recovery rechecks photos before requesting another point`() = runTest {
        delegate.endOrder(key, "tag", listOf(5), emptyList(), emptyList(), requestLocation = { LocationRequestResult.PermissionRequired })
        assertTrue(delegate.hasPendingEndLocation)
        rows = emptyList()
        delegate.retryEndLocation { error("Photos removed during permission wait") }
        assertFalse(delegate.hasPendingEndLocation)
        noSubmission()
    }

    @Test fun `Room failure ends loading and a lost end response never replays the mutation`() = runTest {
        coEvery { images.getImagesByOrderId(key) } throws IOException("database unavailable")
        checkout()
        assertTrue(state.value is NfcSignInUiState.Error)
        noSubmission()
        coEvery { images.getImagesByOrderId(key) } coAnswers { rows }
        coEvery { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Exception(IOException("response lost"))
        checkout()
        assertTrue(state.value is NfcSignInUiState.Error)
        assertFalse(delegate.hasPendingEndLocation)
        delegate.retryEndLocation { error("Unknown HTTP outcome must not auto replay") }
        coVerify(exactly = 1) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private suspend fun checkout() = delegate.endOrder(key, "tag", listOf(5), listOf("stale-before"), listOf("stale-after"),
        requestLocation = { LocationRequestResult.Coordinates(point) })

    private fun noSubmission() = coVerify(exactly = 0) { orders.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
}
