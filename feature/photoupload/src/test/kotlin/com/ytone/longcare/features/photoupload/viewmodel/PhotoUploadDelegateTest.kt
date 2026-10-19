package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploadException
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PhotoUploadDelegateTest {
    @After
    fun cleanup() { unmockkAll() }

    @Test
    fun `upload success waits for Room before publishing uploaded state`() = runTest {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
        val started = CompletableDeferred<Unit>()
        val persist = CompletableDeferred<Unit>()
        val images = mockk<OrderImageRepository>()
        coEvery { images.markAsSuccess(1L, "cloud-key") } coAnswers {
            started.complete(Unit)
            persist.await()
        }
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), mockk())
        queue.imageTasks.value = listOf(
            ImageTask("1", "file:///photo.jpg", ImageTaskType.BEFORE_CARE,
                resultUri = "file:///photo.jpg", status = ImageTaskStatus.SUCCESS),
        )
        val uploader = mockk<PhotoCloudUploader>()
        coEvery { uploader.upload(any(), any()) } returns UploadedPhoto("cloud-key")
        val delegate = PhotoUploadDelegate(uploader, mockk(), mockk(), queue, mockk())

        val pending = async { delegate.uploadSuccessfulImagesToCloud() }
        started.await()
        assertFalse(pending.isCompleted)
        assertFalse(queue.imageTasks.value.single().isUploaded)
        persist.complete(Unit)

        assertEquals(listOf("cloud-key"), pending.await().getOrThrow()[ImageTaskType.BEFORE_CARE])
        assertTrue(queue.imageTasks.value.single().isUploaded)
    }

    @Test
    fun `failed persistence does not mark a photo as uploaded`() = runTest {
        val images = mockk<OrderImageRepository>()
        val failure = java.io.IOException("disk full")
        coEvery { images.markAsSuccess(any(), any()) } throws failure
        val queue = PhotoTaskQueueDelegate(backgroundScope, images, mockk(), mockk())
        queue.imageTasks.value = listOf(ImageTask("1", "file:///photo.jpg", ImageTaskType.BEFORE_CARE))

        try {
            queue.updateTaskUploadStatus("1", "cloud-key")
            fail("Persistence failure must reach the caller")
        } catch (error: java.io.IOException) {
            assertSame(failure, error)
        }
        assertFalse(queue.imageTasks.value.single().isUploaded)
        assertNull(queue.imageTasks.value.single().key)
    }

    @Test
    fun `upload failure preserves sanitized error details through the diagnostic gateway`() = runTest {
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "123"
        val reported = slot<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(capture(reported)) } just Runs
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
        val uploadError = PhotoCloudUploadException("Request expired token=private-token")
        val uploader = mockk<PhotoCloudUploader>()
        coEvery { uploader.upload(any(), any()) } throws uploadError
        val queue = mockk<PhotoTaskQueueDelegate>()
        every { queue.currentOrderKey } returns MutableStateFlow(OrderKey(42))
        every { queue.getTasksSnapshot() } returns listOf(
            ImageTask("1", "file:///photo.jpg", ImageTaskType.BEFORE_CARE,
                resultUri = "file:///photo.jpg", status = ImageTaskStatus.SUCCESS),
        )
        val messages = mockk<PhotoUploadMessages> {
            every { cloudUploadFailed } returns "上传失败"
        }
        val delegate = PhotoUploadDelegate(uploader, mockk(), mockk(), queue, messages)

        val result = delegate.uploadSuccessfulImagesToCloud()

        assertSame(uploadError, result.exceptionOrNull()?.cause)
        assertFalse(delegate.isUploading.value)
        val fields = reported.captured.fields
        assertEquals("123", fields["userId"])
        assertEquals("42", fields["orderId"])
        assertEquals("cloud_upload_failure", fields["eventCode"])
        assertEquals(PhotoCloudUploadException::class.java.name, fields["errorType"])
        assertTrue(fields.getValue("errorMessage").contains("Request expired"))
        assertFalse(reported.captured.message!!.contains("private-token"))
        verify(exactly = 1) { CrashReportGateway.postCaughtException(any()) }
        coVerify(exactly = 0) { queue.updateTaskUploadStatus(any(), any()) }
    }
}
