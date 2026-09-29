package com.ytone.longcare.common.diagnostics

import com.ytone.longcare.common.utils.KLogger
import io.mockk.*
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiagnosticEventTrackerTest {
    @Before
    fun setup() {
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "123"
        every { CrashReportGateway.postCaughtException(any()) } just Runs
        every { CrashReportGateway.recordBreadcrumb(any(), any()) } just Runs
    }

    @After
    fun cleanup() { unmockkAll() }

    @Test
    fun `routine success and cancellation never become caught exceptions`() {
        DiagnosticEventTracker.trackEvent(DiagnosticCategory.CAMERA, "capture_success", "拍照成功")
        DiagnosticEventTracker.trackEvent(DiagnosticCategory.FACE_VERIFICATION, "cancelled", "用户取消")
        DiagnosticEventTracker.trackError(DiagnosticCategory.PHOTO_UPLOAD, "failed", "失败", CancellationException())
        verify(exactly = 0) { CrashReportGateway.postCaughtException(any()) }
        verify(exactly = 2) { CrashReportGateway.recordBreadcrumb(any(), "123") }
    }

    @Test
    fun `error without throwable has explicit severity category and raw user ID`() {
        DiagnosticEventTracker.trackError(DiagnosticCategory.LOCATION, "upload_failed", "位置上传失败")
        verify(exactly = 1) {
            CrashReportGateway.postCaughtException(match {
                it.fields["level"] == "ERROR" && it.fields["userId"] == "123" &&
                    it.fields["category"] == "location" && it.fields["eventCode"] == "upload_failed"
            })
        }
    }
}
