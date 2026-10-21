package com.ytone.longcare.features.location.tracker

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

class LocationDiagnosticsRegressionTest {

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
    fun `routine lifecycle event must not be reported as a caught exception`() {
        LocationEventTracker.trackEvent(
            eventType = LocationEventTracker.EventType.SDK_RECOVERED,
            extras = mapOf("orderId" to 42L),
        )

        verify(exactly = 0) { CrashReportGateway.postCaughtException(any()) }
    }

}
