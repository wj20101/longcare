package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        val uri = mockk<Uri>()
        every { uri.toString() } returns localUri
        every { uri.path } returns "/managed/photo.jpg"
        every { Uri.parse(localUri) } returns uri
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
    fun `capture arriving before Room waits and counts persisted photos against category limit`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val images = mockk<OrderImageRepository>(relaxed = true)
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        val pipeline = mockk<UnifiedImagePipeline>(relaxed = true)
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, pipeline, messages)
        val incomingUri = Uri.parse(localUri)
        queue.setOrderKey(orderKey)
        val adding = async {
            queue.addImagesToProcess(listOf(incomingUri), ImageTaskType.BEFORE_CARE,
                "", maxPhotosPerCategory = 1)
        }
        runCurrent()

        assertFalse("Existing photos must be loaded before calculating available slots", adding.isCompleted)
        coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
        initialLoad.complete(listOf(OrderImageEntity(
            id = 5L, orderId = orderKey.orderId, imageType = ImageType.BEFORE_CARE.value,
            localUri = "file:///managed/existing.jpg",
        )))
        runCurrent()

        assertEquals(1, adding.await().rejectedCount)
        assertEquals(listOf("5"), queue.imageTasks.value.map { it.id })
        coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
        coVerify(exactly = 1) { pipeline.deleteManagedImages(listOf(incomingUri)) }
    }

    @Test
    fun `failed initial read never permits capture as if history were empty`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        // Match the production viewModelScope: await observes the failed async query.
        val queueScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val queue = PhotoTaskQueueDelegate(queueScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        val adding = async {
            runCatching { queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "") }
        }
        runCurrent()
        val failure = IOException("Room read failed")
        initialLoad.completeExceptionally(failure)
        runCurrent()

        val actualFailure = adding.await().exceptionOrNull()
        assertTrue(actualFailure is IOException)
        assertEquals(failure.message, actualFailure?.message)
        assertTrue(queue.imageTasks.value.isEmpty())
        coVerify(exactly = 1) { images.getImagesByOrderId(orderKey) }
        coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
    }

    @Test
    fun `changing order cancels an early capture awaiting the old order history`() = runTest {
        val oldLoad = CompletableDeferred<List<OrderImageEntity>>()
        val nextOrder = OrderKey(43)
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { oldLoad.await() }
        coEvery { images.getImagesByOrderId(nextOrder) } returns emptyList()
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        val adding = async {
            queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "", orderKey)
        }
        runCurrent()
        queue.setOrderKey(nextOrder)
        runCurrent()

        assertTrue(adding.isCancelled)
        assertEquals(nextOrder, queue.currentOrderKey.value)
        assertTrue(queue.imageTasks.value.isEmpty())
        coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
    }

    @Test
    fun `ViewModel reports initial read failure through the existing UI event channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val images = mockk<OrderImageRepository>()
            coEvery { images.getImagesByOrderId(orderKey) } throws IOException("Room read failed")
            val config = mockk<com.ytone.longcare.domain.system.ServicePhotoConfigProvider> {
                coEvery { getMaxServicePhotoCount() } returns 1
            }
            val text = mockk<com.ytone.longcare.common.text.ResourceTextResolver> {
                every { text(any()) } returns "save failed"
            }
            val vm = PhotoProcessingViewModel(mockk(), mockk(), mockk(), mockk(), images, config, text)
            store.put("photos", vm)
            vm.setOrderKey(orderKey)
            vm.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
            val event = async { vm.events.first() }
            runCurrent()

            assertEquals(PhotoProcessingEvent.PhotoProcessingFailed("save failed"), event.await())
            assertTrue(vm.imageTasks.value.isEmpty())
            coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `early capture waits for initial Room restoration before persistence`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } returns 7L
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        val adding = async { queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "") }
        runCurrent()
        assertFalse(adding.isCompleted)
        coVerify(exactly = 0) { images.addImage(any(), any(), any(), any()) }
        initialLoad.complete(emptyList())
        runCurrent()
        adding.await()

        assertEquals("7", queue.imageTasks.value.single().id)
        assertEquals(ImageTaskStatus.SUCCESS, queue.imageTasks.value.single().status)
        coVerify(exactly = 0) { images.deleteImage(any()) }
    }

    @Test
    fun `restored rows remain while a new photo insert is pending`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val insertedId = CompletableDeferred<Long>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } coAnswers { insertedId.await() }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        val adding = async { queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "") }
        runCurrent()
        initialLoad.complete(listOf(OrderImageEntity(
            id = 5L, orderId = 42L, imageType = ImageType.BEFORE_CARE.value,
            localUri = "file:///managed/existing.jpg",
        )))
        runCurrent()
        adding.await()

        assertEquals(2, queue.imageTasks.value.size)
        assertEquals(ImageTaskStatus.PROCESSING, queue.imageTasks.value.single { it.id != "5" }.status)
        insertedId.complete(7L)
        runCurrent()
        assertEquals(listOf("5", "7"), queue.imageTasks.value.map { it.id })
        assertTrue(queue.imageTasks.value.all { it.status == ImageTaskStatus.SUCCESS })
    }

    @Test
    fun `removing a restored photo is preserved when new photo insertion completes`() = runTest {
        val initialLoad = CompletableDeferred<List<OrderImageEntity>>()
        val insertedId = CompletableDeferred<Long>()
        val images = mockk<OrderImageRepository>(relaxed = true)
        coEvery { images.getImagesByOrderId(orderKey) } coAnswers { initialLoad.await() }
        coEvery { images.addImage(any(), any(), any(), any()) } coAnswers { insertedId.await() }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(relaxed = true), messages)
        queue.setOrderKey(orderKey)
        runCurrent()
        initialLoad.complete(listOf(OrderImageEntity(
            id = 5L, orderId = 42L, imageType = ImageType.BEFORE_CARE.value, localUri = localUri,
        )))
        runCurrent()
        queue.removeTask("5")
        assertTrue(queue.imageTasks.value.isEmpty())
        queue.addImagesToProcess(listOf(Uri.parse(localUri)), ImageTaskType.BEFORE_CARE, "")
        runCurrent()
        insertedId.complete(7L)
        runCurrent()
        assertEquals(listOf("7"), queue.imageTasks.value.map { it.id })
        coVerify(exactly = 1) { images.deleteImage(5L) }
        coVerify(exactly = 0) { images.deleteImage(7L) }
    }
}
