package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownAlarm
import com.ytone.longcare.model.OrderKey
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AlarmRingtoneServiceTest {
    private lateinit var context: Context
    private lateinit var manager: CountdownNotificationManager
    private lateinit var playback: AlarmRingtonePlaybackController
    private var controller: ServiceController<AlarmRingtoneService>? = null
    private val orderKey = OrderKey(12345L, 12)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = mockk(relaxed = true)
        playback = mockk(relaxed = true)
        every { playback.isPlaying } returns false
        every { playback.start() } returns true
        every { manager.extractAlarm(any()) } answers {
            val intent = firstArg<Intent>()
            CountdownAlarm(CountdownNotificationManager.extractOrderKey(intent),
                CountdownNotificationManager.extractServiceName(intent, "护理"),
                requireNotNull(intent.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER)),
                intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        }
        every { manager.ownsAlarm(any<CountdownAlarm>()) } returns true
        every { manager.startAlarmForeground(any(), any()) } answers {
            val notification = Notification.Builder(context, "test_countdown")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).build()
            ServiceCompat.startForeground(firstArg(), 2001, notification, 2)
            true
        }
    }

    @After fun tearDown() {
        controller?.destroy()
        unmockkAll()
    }

    @Test fun `thirty second playback limit detaches notification and never directly launches activity`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertFalse(shadowOf(service).isStoppedBySelf)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AlarmRingtoneService.RINGING_TIMEOUT_MS))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(shadowOf(service).notificationShouldRemoved)
        assertNotNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(2001))
        verify(exactly = 1) { playback.stop() }
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test fun `foreground promotion failure stops immediately before playing`() {
        val service = createService()
        every { manager.startAlarmForeground(service, any()) } returns false
        service.onStartCommand(startIntent(), 0, 1)
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { playback.start() }
        verify(exactly = 1) { playback.stop() }
    }

    @Test fun `late stale start cannot stop a newer ringing session`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        val stale = startIntent().apply { putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, OrderKey(999L)) }
        every { manager.ownsAlarm(match<CountdownAlarm> { it.orderKey.orderId == 999L }) } returns false
        service.onStartCommand(stale, 0, 2)
        assertFalse(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { playback.stop() }
    }

    @Test fun `duplicate start does not extend ringing limit or publish twice`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        service.onStartCommand(startIntent(), 0, 2)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { manager.startAlarmForeground(any(), any()) }
        verify(exactly = 1) { playback.start() }
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_started") }
    }

    @Test fun `playback startup failure has a distinct final diagnostic without success`() {
        val service = createService()
        every { playback.start() } returns false
        every { playback.lastStartFailureType } returns "IOException"
        service.onStartCommand(startIntent(), 0, 1)
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_start_failed", "IOException") }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started") }
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `stop uses stopService without creating a replacement service`() {
        AlarmRingtoneService.stopRingtone(context)
        val application = shadowOf(context as Application)
        assertNull(application.nextStartedService)
        assertEquals(AlarmRingtoneService::class.java.name, application.nextStoppedService.component?.className)
    }

    private fun createService(): AlarmRingtoneService {
        val created = Robolectric.buildService(AlarmRingtoneService::class.java)
        val service = created.get()
        // 本测试仅替换 Hilt 注入，保留真实 Service / Handler / 前台通知生命周期。
        ReflectionHelpers.setField(service, "injected", true)
        service.countdownNotificationManager = manager
        created.create()
        ReflectionHelpers.setField(service, "playbackController", playback)
        controller = created
        return service
    }

    private fun startIntent() = Intent(context, AlarmRingtoneService::class.java).apply {
        action = AlarmRingtoneService.ACTION_START_RINGTONE
        putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
        putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, "护理")
        putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, "1:2:3")
        putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 1234L)
    }
}
