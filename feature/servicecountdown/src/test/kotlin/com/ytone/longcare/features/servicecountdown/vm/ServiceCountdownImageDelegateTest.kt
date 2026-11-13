package com.ytone.longcare.features.servicecountdown.vm

import android.util.Log
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceCountdownImageDelegateTest {
    private val repository = mockk<OrderImageRepository>()
    private val key = OrderKey(7)
    private val before = row(7, ImageType.BEFORE_CARE, "before")
    private val after = row(7, ImageType.AFTER_CARE, "after")
    private val state = ServiceCountdownStateHolder()

    @Before fun setup() { mockkStatic(Log::class); every { Log.println(any(), any(), any()) } returns 0 }
    @After fun cleanup() = unmockkAll()

    @Test fun `pending failed or missing key cannot pass or appear uploaded`() = runTest {
        val delegate = ServiceCountdownImageDelegate(state, repository, backgroundScope)
        coEvery { repository.getImagesByOrderId(key) } returns listOf(
            before.copy(uploadStatus = ImageUploadStatus.PENDING.value), after.copy(cloudKey = null))
        assertFalse(delegate.validatePhotosUploaded(key))
        assertTrue(state.uploadedImages.value.isEmpty())
        coEvery { repository.getImagesByOrderId(key) } returns listOf(before, after,
            row(7, ImageType.CENTER_CARE, "center").copy(uploadStatus = ImageUploadStatus.FAILED.value))
        assertFalse(delegate.validatePhotosUploaded(key))
    }

    @Test fun `empty database read clears old photos and every button validation rereads Room`() = runTest {
        val delegate = ServiceCountdownImageDelegate(state, repository, backgroundScope)
        var rows = listOf(before, after)
        coEvery { repository.getImagesByOrderId(key) } coAnswers { rows }
        assertTrue(delegate.validatePhotosUploaded(key))
        assertFalse(state.uploadedImages.value.isEmpty())
        rows = emptyList()
        assertFalse(delegate.validatePhotosUploaded(key))
        assertTrue(state.uploadedImages.value.isEmpty())
        coVerify(exactly = 2) { repository.getImagesByOrderId(key) }
    }

    @Test fun `late previous order validation cannot approve the old order or refill current photo state`() = runTest {
        val delegate = ServiceCountdownImageDelegate(state, repository, backgroundScope)
        val oldRows = CompletableDeferred<List<OrderImageEntity>>()
        coEvery { repository.getImagesByOrderId(key) } coAnswers { oldRows.await() }
        val old = async { delegate.validatePhotosUploaded(key) }
        runCurrent()
        val next = OrderKey(8)
        coEvery { repository.getImagesByOrderId(next) } returns listOf(row(8, ImageType.BEFORE_CARE, "next"))
        assertFalse(delegate.validatePhotosUploaded(next))
        oldRows.complete(listOf(before, after))
        assertFalse(old.await())
        assertEquals(listOf("next"), state.uploadedImages.value[ImageTaskType.BEFORE_CARE]?.map { it.key })
        assertFalse(state.uploadedImages.value.containsKey(ImageTaskType.AFTER_CARE))
    }

    @Test fun `late successful validation cannot override a newer empty read of the same order`() = runTest {
        val delegate = ServiceCountdownImageDelegate(state, repository, backgroundScope)
        val oldRows = CompletableDeferred<List<OrderImageEntity>>()
        var reads = 0
        coEvery { repository.getImagesByOrderId(key) } coAnswers {
            if (++reads == 1) oldRows.await() else emptyList()
        }
        val old = async { delegate.validatePhotosUploaded(key) }
        runCurrent()
        assertFalse(delegate.validatePhotosUploaded(key))
        oldRows.complete(listOf(before, after))
        assertFalse(old.await())
        assertTrue(state.uploadedImages.value.isEmpty())
    }

    private fun row(orderId: Long, type: ImageType, key: String) = OrderImageEntity(orderId = orderId,
        imageType = type.value, localUri = "file:///test", uploadStatus = ImageUploadStatus.SUCCESS.value, cloudKey = key)
}
