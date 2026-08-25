package com.ytone.longcare.features.identification.tracker

import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.KLogger
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class FaceVerificationEventTrackerTest {
    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.postCaughtException(any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `routine face event is not reported as a caught exception`() {
        FaceVerificationEventTracker.trackEvent(
            FaceVerificationEventTracker.EventType.FACE_INIT_SUCCESS,
        )

        verify(exactly = 0) { CrashReportGateway.postCaughtException(any()) }
    }

    @Test
    fun `face error is reported as a caught exception`() {
        FaceVerificationEventTracker.trackError(
            FaceVerificationEventTracker.EventType.FACE_VERIFY_ERROR,
        )

        verify(exactly = 1) { CrashReportGateway.postCaughtException(any()) }
    }
}
