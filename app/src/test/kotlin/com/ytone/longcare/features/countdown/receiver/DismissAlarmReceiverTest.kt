package com.ytone.longcare.features.countdown.receiver

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.features.countdown.manager.CountdownIntentPurpose
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DismissAlarmReceiverTest {
    private lateinit var context: Context
    private val manager = mockk<CountdownNotificationManager>(relaxed = true)
    private val gate = mockk<CountdownTaskExecutionGate>()
    private val codec = CountdownTaskCodec()
    private val payload = codec.currentPayload(
        UserStorageLease(UserScopeKey(1, 2, 3), SessionEpoch(4), StorageGeneration(5)),
        OrderKey(orderId = 54321, planId = 21),
        "护理服务",
        8_000,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(AlarmRingtoneService.Companion)
        every { AlarmRingtoneService.stopRingtone(any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `current dismiss uses same identity and broadcasts scoped stop`() {
        every { gate.isCurrent(payload) } returns true

        DismissAlarmReceiverDelegate.handle(context, payload, manager, codec, gate)

        verify(exactly = 1) { AlarmRingtoneService.stopRingtone(context) }
        verify(exactly = 1) { manager.dismiss(payload) }
        val broadcasts = shadowOf(context as Application).broadcastIntents
        val stopIntent = broadcasts.lastOrNull { it.action == DismissAlarmReceiver.ACTION_STOP_ALARM }
        assertNotNull(stopIntent)
        assertEquals(payload, codec.fromIntent(stopIntent, CountdownIntentPurpose.DISMISS))
    }

    @Test
    fun `stale dismiss cannot stop current ringtone or notification`() {
        every { gate.isCurrent(payload) } returns false

        DismissAlarmReceiverDelegate.handle(context, payload, manager, codec, gate)

        verify(exactly = 0) { AlarmRingtoneService.stopRingtone(any()) }
        verify(exactly = 0) { manager.dismiss(any()) }
    }
}
