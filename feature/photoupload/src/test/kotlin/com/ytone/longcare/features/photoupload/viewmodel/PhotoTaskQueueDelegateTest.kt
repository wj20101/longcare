package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderImageEntity
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoTaskQueueDelegateTest {
    private val orderKey = OrderKey(42)
    private val localUri = "file:///managed/photo.jpg"
    private val messages = PhotoUploadMessages("", "", "", "", "", "upload failed", "save failed")

    @Before
    fun setup() {
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "test-user"
        every { CrashReportGateway.postCaughtException(any()) } just Runs
        mockkStatic(Uri::class)
        every { Uri.parse(localUri) } returns mockk {
            every { toString() } returns localUri
            every { path } returns "/managed/photo.jpg"
        }
    }

    @After
    fun cleanup() = unmockkAll()

    @Test
    fun `failed insert retains photo and retry persists before success`() = runTest {
        val images = mockk<OrderImageRepository>()
        val pipeline = mockk<UnifiedImagePipeline>(relaxed = true)
        var attempts = 0
        coEvery { images.addImage(orderKey, ImageType.BEFORE_CARE, localUri, any()) } coAnswers {
            if (++attempts == 1) throw IOException("disk full")
            7L
        }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, pipeline, messages)
        queue.currentOrderKey.value = orderKey

        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
        runCurrent()
        val failedTask = queue.imageTasks.value.single()
        assertEquals(ImageTaskStatus.FAILED, failedTask.status)
        assertEquals(localUri, failedTask.originalUri)
        assertFalse(failedTask.isUploaded)
        assertTrue(queue.imageTasks.value.none { it.status == ImageTaskStatus.SUCCESS })

        queue.retryTask(failedTask.id)
        runCurrent()

        val recoveredTask = queue.imageTasks.value.single()
        assertEquals("7", recoveredTask.id)
        assertEquals(ImageTaskStatus.SUCCESS, recoveredTask.status)
        assertEquals(localUri, recoveredTask.resultUri)
        assertFalse(recoveredTask.isUploaded)
        assertEquals(2, attempts)
        coVerify(exactly = 0) { pipeline.deleteManagedImages(any()) }
    }

    @Test
    fun `repeated failed insert never becomes a successful UUID task`() = runTest {
        val images = mockk<OrderImageRepository>()
        coEvery { images.addImage(any(), any(), any(), any()) } throws IOException("disk full")
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(relaxed = true), messages)
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "", orderKey)
        runCurrent()
        queue.retryTask(queue.imageTasks.value.single().id)
        runCurrent()

        assertEquals(ImageTaskStatus.FAILED, queue.imageTasks.value.single().status)
        coVerify(exactly = 2) { images.addImage(any(), any(), any(), any()) }
    }

    @Test
    fun `order UUID cannot publish uploaded state but no-order preview can`() = runTest {
        val images = mockk<OrderImageRepository>()
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.imageTasks.value = listOf(ImageTask("temporary-id", localUri, ImageTaskType.BEFORE_CARE))
        queue.currentOrderKey.value = orderKey

        try {
            queue.updateTaskUploadStatus("temporary-id", "cloud-key")
            fail("Order photos require a durable numeric row id")
        } catch (_: IllegalStateException) {
            assertFalse(queue.imageTasks.value.single().isUploaded)
        }

        queue.currentOrderKey.value = null
        queue.updateTaskUploadStatus("temporary-id", "cloud-key")
        assertTrue(queue.imageTasks.value.single().isUploaded)
        coVerify(exactly = 0) { images.markAsSuccess(any(), any()) }
    }

    @Test
    fun `removing photo during insert cleans up late record and never restores task`() = runTest {
        val insert = CompletableDeferred<Long>()
        val images = mockk<OrderImageRepository>(relaxed = true)
        coEvery { images.addImage(any(), any(), any(), any()) } coAnswers { insert.await() }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(relaxed = true), messages)
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "", orderKey)
        runCurrent()
        queue.removeTask(queue.imageTasks.value.single().id)
        insert.complete(7L)
        runCurrent()

        assertTrue(queue.imageTasks.value.isEmpty())
        coVerify(exactly = 1) { images.deleteImage(7L) }
    }

    @Test
    fun `delayed initial Room snapshot keeps a newly captured and persisted photo`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } returns 7L
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
        runCurrent()
        initialLoad.complete(emptyList())
        runCurrent()

        assertEquals("7", queue.imageTasks.value.single().id)
        assertEquals(ImageTaskStatus.SUCCESS, queue.imageTasks.value.single().status)
        coVerify(exactly = 0) { images.deleteImage(any()) }
    }

    @Test
    fun `initial query seeing committed row before insert returns cannot duplicate pending photo`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val insertedId = CompletableDeferred<Long>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } coAnswers { insertedId.await() }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
        runCurrent()
        initialLoad.complete(listOf(OrderImageEntity(
            id = 7L, orderId = 42L, imageType = ImageType.BEFORE_CARE.value, localUri = localUri,
        )))
        runCurrent()

        assertEquals(1, queue.imageTasks.value.size)
        assertEquals(ImageTaskStatus.PROCESSING, queue.imageTasks.value.single().status)
        insertedId.complete(7L)
        runCurrent()
        assertEquals("7", queue.imageTasks.value.single().id)
        assertEquals(ImageTaskStatus.SUCCESS, queue.imageTasks.value.single().status)
    }

    @Test
    fun `initial query cannot resurrect removed photo while insert return is delayed`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val insertedId = CompletableDeferred<Long>()
        val images = mockk<OrderImageRepository>(relaxed = true)
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } coAnswers { insertedId.await() }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(relaxed = true), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
        runCurrent()
        queue.removeTask(queue.imageTasks.value.single().id)
        initialLoad.complete(listOf(OrderImageEntity(
            id = 7L, orderId = 42L, imageType = ImageType.BEFORE_CARE.value, localUri = localUri,
        )))
        runCurrent()
        assertTrue(queue.imageTasks.value.isEmpty())

        insertedId.complete(7L)
        runCurrent()
        assertTrue(queue.imageTasks.value.isEmpty())
        coVerify(exactly = 1) { images.deleteImage(7L) }
    }
}
