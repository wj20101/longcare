package com.ytone.longcare.features.countdown.tracker

import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics.ClockSample
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownAlarmDiagnosticsTest {
    @Before fun setUp() {
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "3"
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `actual RTC deadline overrides stable alarm identity after clock rebase`() {
        val intent = Intent()
        CountdownAlarmDiagnostics.scheduled(intent, "alarm_clock", null, true, plannedAtMillis = 2_000L)
        val data = requireNotNull(CountdownAlarmDiagnostics.read(intent))
        data.putLong("receiverWall", 2_050L)

        val fields = CountdownAlarmDiagnostics.fields(data, 1_000L, "validated")

        assertEquals(2_000L, fields["plannedAt"])
        assertEquals(1_000L, fields["alarmId"])
        assertEquals(50L, fields["receiverDelayMs"])
        assertEquals("app_on_receive", fields["receiverDelayBoundary"])
    }

    @Test fun `legacy identity and missing worker evidence never become measured clocks`() {
        val data = Bundle()
        CountdownAlarmDiagnostics.playbackStarted(data, nativeAt = null, callbackAt = ClockSample(1_000L, 500L, 400L))
        val fields = CountdownAlarmDiagnostics.fields(data, 123L, "playback_started")

        assertNull(fields["plannedAt"])
        assertNull(fields["receiverDelayMs"])
        assertNull(fields["nativeStartedClock"])
        assertNull(fields["playbackCallbackQueueMs"])
        assertNull(fields["wakeLockHeldByApp"])
    }

    @Test fun `receiver finish is independent from the bundle already dispatched to service`() {
        val intent = Intent()
        val original = baseData()
        CountdownAlarmDiagnostics.attach(intent, original)
        val serviceCopy = Bundle(original)
        CountdownAlarmDiagnostics.validationStarted(intent, ClockSample(1_001_100L, 11_100L, 6_100L))

        val finished = CountdownAlarmDiagnostics.receiverFinished(intent, ClockSample(1_003_100L, 13_100L, 6_600L))
        val fields = CountdownAlarmDiagnostics.fields(finished, 1_001_000L, "receiver_finish_returned")

        assertEquals(100L, fields["validationQueueMs"])
        assertEquals(2_100L, fields["receiverLifetimeMs"])
        assertEquals(1_500L, fields["receiverSleepMs"])
        assertEquals("1003100/13100/6600", fields["receiverFinishClock"])
        assertFalse(original.containsKey("receiverFinishElapsed"))
        assertFalse(serviceCopy.containsKey("receiverFinishElapsed"))
    }

    @Test fun `main callback latency is separate from native start despite wall clock rollback`() {
        val data = baseData()
        CountdownAlarmDiagnostics.serviceEntered(data, ClockSample(2_000L, 11_900L, 6_900L), ClockSample(2_100L, 12_000L, 7_000L))
        CountdownAlarmDiagnostics.playbackPrepared(data, ClockSample(2_200L, 12_100L, 7_100L), ClockSample(1_000L, 12_600L, 7_600L))
        CountdownAlarmDiagnostics.playbackStarted(data, ClockSample(1_100L, 12_700L, 7_700L), ClockSample(1_800L, 13_400L, 8_400L))

        val fields = CountdownAlarmDiagnostics.fields(data, 1_001_000L, "playback_started")

        assertEquals(500L, fields["preparedCallbackQueueMs"])
        assertEquals(600L, fields["preparedToNativeStartMs"])
        assertEquals(700L, fields["playbackCallbackQueueMs"])
        assertEquals(700L, fields["serviceToNativeStartMs"])
        assertEquals("start_returned", fields["nativeStartBoundary"])
    }

    @Test fun `worker progress without delivered main callbacks stays distinguishable`() {
        val data = Bundle()
        CountdownAlarmDiagnostics.playbackProgress(data, ClockSample(1_000L, 100L, 90L), ClockSample(1_200L, 300L, 290L))

        val fields = CountdownAlarmDiagnostics.fields(data, 1_001_000L, "playback_total_timeout")

        assertEquals("1000/100/90", fields["workerPreparedClock"])
        assertEquals("1200/300/290", fields["nativeStartedClock"])
        assertNull(fields["preparedCallbackQueueMs"])
        assertNull(fields["playbackCallbackQueueMs"])
        assertNull(fields["playbackCallbackClock"])
    }

    @Test fun `failed or unavailable app wake lock does not fabricate acquisition`() {
        val data = baseData()
        CountdownAlarmDiagnostics.wakeLockAttempted(data, null, "SecurityException", ClockSample(1_002_010L, 12_010L, 7_010L))

        val fields = CountdownAlarmDiagnostics.fields(data, 1_001_000L, "service_entered")

        assertEquals("SecurityException", fields["wakeLockFailure"])
        assertNull(fields["wakeLockHeldByApp"])
        assertNull(fields["serviceToWakeLockMs"])
    }

    @Test fun `stage clocks and identity survive the real shared payload bounds`() {
        val captured = slot<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(capture(captured)) } just runs
        val data = baseData()
        val intent = Intent().also { CountdownAlarmDiagnostics.attach(it, data) }
        CountdownAlarmDiagnostics.validationStarted(intent, ClockSample(1_001_050L, 11_050L, 6_050L))
        data.clock("validated", ClockSample(1_001_100L, 11_100L, 6_100L))
        CountdownAlarmDiagnostics.serviceEntered(data, ClockSample(1_001_200L, 11_200L, 6_200L), ClockSample(1_001_300L, 11_300L, 6_300L))
        CountdownAlarmDiagnostics.wakeLockAttempted(data, true, at = ClockSample(1_001_310L, 11_310L, 6_310L))
        CountdownAlarmDiagnostics.playbackPrepared(data, ClockSample(1_001_400L, 11_400L, 6_400L), ClockSample(1_001_500L, 11_500L, 6_500L))
        CountdownAlarmDiagnostics.playbackStarted(data, ClockSample(1_001_600L, 11_600L, 6_600L), ClockSample(1_001_900L, 11_900L, 6_900L))

        val cases = listOf(
            Triple("receiver_finish_returned", CountdownEventTracker.EventType.ALARM_RECEIVER_FINISHED,
                listOf("receiverClock", "validationStartedClock", "validatedClock", "receiverFinishClock", "validationQueueMs", "receiverSleepMs")),
            Triple("service_entered", CountdownEventTracker.EventType.RINGTONE_SERVICE_START,
                listOf("validatedClock", "serviceCreatedClock", "serviceClock", "wakeLockAttemptClock", "wakeLockHeldByApp", "serviceDispatchSleepMs")),
            Triple("playback_started", CountdownEventTracker.EventType.ALARM_DELIVERY_RESULT,
                listOf("workerPreparedClock", "preparedCallbackClock", "nativeStartedClock", "playbackCallbackClock", "preparedCallbackQueueMs", "playbackCallbackQueueMs")),
        )
        val epochOffset = 1_800_000_000_000L
        for ((outcome, event, requiredKeys) in cases) {
            val evidence = if (outcome == "receiver_finish_returned") {
                CountdownAlarmDiagnostics.receiverFinished(intent, ClockSample(1_001_150L, 11_150L, 6_150L))
            } else data
            // Production-width wall and uptime values expose truncation hidden by tiny test clocks.
            val realistic = Bundle(requireNotNull(evidence)).apply {
                keySet().toList().forEach { key ->
                    when {
                        key.endsWith("Wall") || key == "plannedAt" -> putLong(key, getLong(key) + epochOffset)
                        key.endsWith("Elapsed") -> putLong(key, getLong(key) + 500_000_000L)
                        key.endsWith("Uptime") -> putLong(key, getLong(key) + 400_000_000L)
                    }
                }
            }
            CountdownEventTracker.trackEvent(event, 12345L,
                CountdownAlarmDiagnostics.fields(realistic, epochOffset + 1_001_000L, outcome), reportToServer = true, userId = "3")
            val fields = captured.captured.fields
            assertEquals("12345", fields["orderId"])
            assertEquals((epochOffset + 1_001_000L).toString(), fields["alarmId"])
            assertEquals("wall/elapsed/uptime", fields["clockOrder"])
            requiredKeys.forEach { assertTrue("$outcome lost $it: $fields", fields.containsKey(it)) }
            assertTrue(requireNotNull(captured.captured.message).length <= 950)
        }
    }

    private fun baseData() = Bundle().apply {
        putString("mode", "alarm_clock")
        putLong("plannedAt", 1_001_000L)
        putBoolean("canScheduleExact", true)
        clock("schedule", ClockSample(1_000_000L, 10_000L, 5_000L))
        clock("receiver", ClockSample(1_001_000L, 11_000L, 6_000L))
    }

    private fun Bundle.clock(stage: String, at: ClockSample) {
        putLong("${stage}Wall", at.wall)
        putLong("${stage}Elapsed", at.elapsed)
        putLong("${stage}Uptime", at.uptime)
    }
}
