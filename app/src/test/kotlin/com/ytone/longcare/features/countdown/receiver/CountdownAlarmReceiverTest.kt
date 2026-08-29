package com.ytone.longcare.features.countdown.receiver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
    private val codec = CountdownTaskCodec()
    private val gate = mockk<CountdownTaskExecutionGate>()
    private val payload = codec.currentPayload(
        UserStorageLease(UserScopeKey(1, 2, 3), SessionEpoch(4), StorageGeneration(5)),
        OrderKey(orderId = 12345, planId = 12),
        "护理服务",
        6_000,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(CountdownEventTracker)
        mockkObject(CountdownForegroundService.Companion)
        mockkObject(AlarmRingtoneService.Companion)
        every { CountdownEventTracker.trackEvent(any(), any(), any()) } just runs
        every { CountdownForegroundService.stopCountdown(any()) } just runs
        every { AlarmRingtoneService.startRingtone(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `current scoped alarm starts only its ringtone flow`() {
        every { gate.isCurrent(payload) } returns true

        CountdownAlarmReceiverDelegate.handle(context, payload, codec, gate)

        verify(exactly = 1) {
            CountdownEventTracker.trackEvent(
                CountdownEventTracker.EventType.ALARM_TRIGGERED,
                payload.orderKey.orderId,
                any(),
            )
        }
        verify(exactly = 1) { CountdownForegroundService.stopCountdown(context) }
        verify(exactly = 1) { AlarmRingtoneService.startRingtone(context, payload, codec) }
    }

    @Test
    fun `stale user alarm is silent before every side effect`() {
        every { gate.isCurrent(payload) } returns false

        CountdownAlarmReceiverDelegate.handle(context, payload, codec, gate)

        verify(exactly = 0) { CountdownEventTracker.trackEvent(any(), any(), any()) }
        verify(exactly = 0) { CountdownForegroundService.stopCountdown(any()) }
        verify(exactly = 0) { AlarmRingtoneService.startRingtone(any(), any(), any()) }
    }
}
