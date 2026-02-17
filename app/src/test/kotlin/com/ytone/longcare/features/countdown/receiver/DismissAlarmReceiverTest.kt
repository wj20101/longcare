package com.ytone.longcare.features.countdown.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.model.OrderKey
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
    private lateinit var countdownNotificationManager: CountdownNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        countdownNotificationManager = mockk(relaxed = true)

        mockkObject(AlarmRingtoneService.Companion)
        every { AlarmRingtoneService.stopRingtone(any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun onReceive_shouldHandleLegacyExtrasAndBroadcastStopAlarm() {
        val orderKey = OrderKey(orderId = 54321L, planId = 21)
        val intent = Intent().apply {
            putExtra("extra_request", orderKey)
            putExtra("service_name", "旧服务名")
        }

        DismissAlarmReceiverDelegate.handle(context, intent, countdownNotificationManager)

        verify(exactly = 1) { AlarmRingtoneService.stopRingtone(context) }
        verify(exactly = 1) { countdownNotificationManager.cancelCountdownCompletionNotification() }

        val broadcasts = shadowOf(context as Application).broadcastIntents
        val stopAlarmIntent = broadcasts.lastOrNull { it.action == DismissAlarmReceiver.ACTION_STOP_ALARM }
        assertNotNull(stopAlarmIntent)

        val extractedOrderKey = IntentCompat.getParcelableExtra(
            stopAlarmIntent!!,
            CountdownNotificationManager.EXTRA_ORDER_KEY,
            OrderKey::class.java
        )
        assertEquals(orderKey, extractedOrderKey)
        assertEquals(
            "旧服务名",
            stopAlarmIntent.getStringExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME)
        )
    }
}
