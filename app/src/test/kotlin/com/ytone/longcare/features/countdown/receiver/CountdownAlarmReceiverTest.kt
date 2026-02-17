package com.ytone.longcare.features.countdown.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.features.countdown.worker.CountdownBackupWorker
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.model.OrderKey
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CountdownAlarmReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        mockkObject(CountdownEventTracker)
        mockkObject(CountdownBackupWorker.Companion)
        mockkObject(CountdownForegroundService.Companion)
        mockkObject(AlarmRingtoneService.Companion)

        every { CountdownEventTracker.trackEvent(any(), any(), any()) } just runs
        every { CountdownBackupWorker.markAlarmTriggered(any(), any()) } just runs
        every { CountdownForegroundService.stopCountdown(any()) } just runs
        every { AlarmRingtoneService.startRingtone(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun onReceive_shouldHandleLegacyExtrasAndStartAlarmFlow() {
        val orderKey = OrderKey(orderId = 12345L, planId = 12)
        val intent = Intent().apply {
            putExtra("extra_request", orderKey)
            putExtra("service_name", "旧服务名")
        }

        CountdownAlarmReceiverDelegate.handle(context, intent)

        verify(exactly = 1) {
            CountdownEventTracker.trackEvent(
                CountdownEventTracker.EventType.ALARM_TRIGGERED,
                orderKey.orderId,
                any()
            )
        }
        verify(exactly = 1) { CountdownBackupWorker.markAlarmTriggered(context, orderKey.orderId) }
        verify(exactly = 1) { CountdownForegroundService.stopCountdown(context) }
        verify(exactly = 1) { AlarmRingtoneService.startRingtone(context, orderKey, "旧服务名") }
    }

    @Test
    fun onReceive_shouldReturnEarlyWhenOrderIdMissing() {
        val intent = Intent().apply {
            putExtra("service_name", "旧服务名")
        }

        CountdownAlarmReceiverDelegate.handle(context, intent)

        verify(exactly = 0) { CountdownEventTracker.trackEvent(any(), any(), any()) }
        verify(exactly = 0) { CountdownBackupWorker.markAlarmTriggered(any(), any()) }
        verify(exactly = 0) { CountdownForegroundService.stopCountdown(any()) }
        verify(exactly = 0) { AlarmRingtoneService.startRingtone(any(), any(), any()) }
    }
}
