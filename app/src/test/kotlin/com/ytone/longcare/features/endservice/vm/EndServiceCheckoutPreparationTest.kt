package com.ytone.longcare.features.endservice.vm

import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EndServiceCheckoutPreparationTest {
    private val images = mockk<OrderImageRepository>()
    private val key = OrderKey(7, 3)
    private lateinit var vm: EndServiceSelectionViewModel

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val details = mockk<OrderDetailRepository> {
            every { getCachedOrderInfo(any()) } returns ServiceOrderInfoModel(projectList = listOf(
                ServiceProjectM(projectId = 1), ServiceProjectM(projectId = 2)))
        }
        vm = EndServiceSelectionViewModel(details, mockk<ResourceTextResolver> { every { text(any()) } returns "photos error" }, images)
        coEvery { images.getImagesByOrderId(any()) } coAnswers { rows(firstArg<OrderKey>().orderId) }
    }
    @After fun cleanup() { Dispatchers.resetMain(); unmockkAll() }

    @Test fun `one Room read freezes projects end type and validated photos while duplicate clicks are ignored`() = runTest {
        vm.initData(key, listOf(1, 2)); runCurrent()
        val response = CompletableDeferred<List<OrderImageEntity>>()
        coEvery { images.getImagesByOrderId(key) } coAnswers { response.await() }
        val preparation = async { vm.prepareCheckout(key, 2) }
        runCurrent()
        assertTrue(vm.isPreparingCheckout.value)
        vm.toggleSelection(1)
        assertNull(vm.prepareCheckout(key, 1))
        response.complete(rows(7))
        val info = (preparation.await() as CheckoutPreparation.Ready).info
        assertEquals(listOf(1, 2), info.projectIdList)
        assertEquals(listOf("before"), info.beginImgList)
        assertEquals(listOf("after"), info.endImgList)
        assertEquals(2, info.endType)
        assertFalse(vm.isPreparingCheckout.value)
        coVerify(exactly = 1) { images.getImagesByOrderId(key) }
    }

    @Test fun `missing uploads and database failures reject navigation and release the button`() = runTest {
        vm.initData(key, listOf(1)); runCurrent()
        coEvery { images.getImagesByOrderId(key) } returns emptyList()
        assertTrue(vm.prepareCheckout(key, 1) is CheckoutPreparation.Rejected)
        coEvery { images.getImagesByOrderId(key) } throws IOException("unavailable")
        assertTrue(vm.prepareCheckout(key, 1) is CheckoutPreparation.Rejected)
        assertFalse(vm.isPreparingCheckout.value)
    }

    @Test fun `changing orders discards an old preparation and never navigates with its photos`() = runTest {
        vm.initData(key, listOf(1)); runCurrent()
        val response = CompletableDeferred<List<OrderImageEntity>>()
        coEvery { images.getImagesByOrderId(key) } coAnswers { response.await() }
        val old = async { vm.prepareCheckout(key, 1) }
        runCurrent()
        val next = OrderKey(8)
        vm.initData(next, listOf(2)); runCurrent()
        response.complete(rows(7))
        assertNull(old.await())
        val info = (vm.prepareCheckout(next, 1) as CheckoutPreparation.Ready).info
        assertEquals(listOf(2), info.projectIdList)
    }

    @Test fun `leaving during preparation propagates cancellation and releases the button`() = runTest {
        vm.initData(key, listOf(1)); runCurrent()
        coEvery { images.getImagesByOrderId(key) } coAnswers { awaitCancellation() }
        val pending = async { vm.prepareCheckout(key, 1) }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertFalse(vm.isPreparingCheckout.value)
    }

    private fun rows(orderId: Long) = listOf(ImageType.BEFORE_CARE to "before", ImageType.AFTER_CARE to "after")
        .map { (type, key) -> OrderImageEntity(orderId = orderId, imageType = type.value,
            localUri = "file:///test", uploadStatus = ImageUploadStatus.SUCCESS.value, cloudKey = key) }
}
