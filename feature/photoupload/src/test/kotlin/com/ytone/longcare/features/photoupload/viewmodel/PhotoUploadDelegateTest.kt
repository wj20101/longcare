package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploadException
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PhotoUploadDelegateTest {
    @After
    fun cleanup() { unmockkAll() }

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
        verify(exactly = 0) { queue.updateTaskUploadStatus(any(), any()) }
    }
}
