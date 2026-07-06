package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.model.OrderKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlarmRingtoneServiceTest {

    private lateinit var context: Context
    private lateinit var serviceComponent: ComponentName

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        serviceComponent = ComponentName(context, AlarmRingtoneService::class.java)
        AlarmRingtoneActivityVisibilityTracker.markVisible(false)
    }

    @After
    fun tearDown() {
        AlarmRingtoneActivityVisibilityTracker.markVisible(false)
    }

    @Test
    fun startRingtone_shouldStartExplicitRingtoneServiceIntent() {
        val orderKey = OrderKey(orderId = 12345L, planId = 12)

        AlarmRingtoneService.startRingtone(context, orderKey, "护理服务")

        val startedIntent = shadowOf(context as Application).nextStartedService
        assertEquals(serviceComponent, startedIntent.component)
        assertEquals(AlarmRingtoneService.ACTION_START_RINGTONE, startedIntent.action)
        assertEquals(orderKey, CountdownNotificationManager.extractOrderKey(startedIntent))
        assertEquals(
            "护理服务",
            CountdownNotificationManager.extractServiceName(startedIntent, "")
        )
    }

    @Test
    fun stopRingtone_shouldSendStopCommandAndStopExplicitService() {
        AlarmRingtoneService.stopRingtone(context)

        val shadowApplication = shadowOf(context as Application)
        val startedIntent = shadowApplication.nextStartedService
        val stoppedIntent = shadowApplication.nextStoppedService

        assertEquals(serviceComponent, startedIntent.component)
        assertEquals(AlarmRingtoneService.ACTION_STOP_RINGTONE, startedIntent.action)
        assertEquals(serviceComponent, stoppedIntent.component)
        assertEquals(AlarmRingtoneService.ACTION_STOP_RINGTONE, stoppedIntent.action)
    }

    @Test
    fun alarmActivityVisibilityTracker_shouldReflectVisibleState() {
        assertFalse(AlarmRingtoneActivityVisibilityTracker.isVisible())

        AlarmRingtoneActivityVisibilityTracker.markVisible(true)
        assertTrue(AlarmRingtoneActivityVisibilityTracker.isVisible())

        AlarmRingtoneActivityVisibilityTracker.markVisible(false)
        assertFalse(AlarmRingtoneActivityVisibilityTracker.isVisible())
    }
}
