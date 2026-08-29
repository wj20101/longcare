package com.ytone.longcare.features.servicecountdown.service

import android.content.Context
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.model.UserScopeKey
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ServiceCountdownSystemGatewayImplTest {
    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<CountdownNotificationManager>(relaxed = true)
    private val taskCodec = CountdownTaskCodec()

    @Before
    fun setUp() {
        mockkObject(CountdownForegroundService.Companion)
        mockkObject(AlarmRingtoneService.Companion)
        every { CountdownForegroundService.stopCountdown(any()) } just runs
        every { AlarmRingtoneService.stopRingtone(any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(CountdownForegroundService.Companion)
        unmockkObject(AlarmRingtoneService.Companion)
    }

    @Test
    fun `session cleanup stops countdown ringtone and scheduled alarm`() = runTest {
        val gateway = ServiceCountdownSystemGatewayImpl(context, notificationManager, taskCodec)

        val identity = SessionRuntimeIdentity(
            scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = 3),
            sessionEpoch = SessionEpoch(4),
        )

        gateway.cleanup(identity)

        verify(exactly = 1) { CountdownForegroundService.stopCountdown(context) }
        verify(exactly = 1) { AlarmRingtoneService.stopRingtone(context) }
        coVerify(exactly = 1) { notificationManager.cleanup(identity) }
    }
}
