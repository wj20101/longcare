package com.ytone.longcare.features.countdown.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.Handler
import android.os.SystemClock
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController.PlaybackEvent
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
    private data class Attempt(val prepared: (Long) -> Unit, val event: (PlaybackEvent) -> Unit)
    private val attempts = mutableListOf<Attempt>()
    private var autoPrepare = true
    private var autoStart = true

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = mockk(relaxed = true)
        playback = mockk(relaxed = true)
        every { playback.isAudibleFallbackSafe } returns true
        every { playback.prepare(any(), any(), any(), any(), any()) } answers {
            val attempt = Attempt(arg(3), arg(4))
            attempts += attempt
            if (autoPrepare) attempt.prepared(attempts.size.toLong())
        }
        every { playback.play(any()) } answers {
            val attempt = attempts[firstArg<Long>().toInt() - 1]
            if (autoStart) Handler(Looper.getMainLooper()).post {
                attempt.event(PlaybackEvent.Started(SystemClock.elapsedRealtime(), 0))
            }
            true
        }
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
        shadowOf(Looper.getMainLooper()).idle()
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
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { playback.prepare(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { playback.stop() }
    }

    @Test fun `late stale start cannot stop a newer ringing session`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        val stale = startIntent().apply { putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, OrderKey(999L)) }
        every { manager.ownsAlarm(match<CountdownAlarm> { it.orderKey.orderId == 999L }) } returns false
        service.onStartCommand(stale, 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { playback.stop() }
    }

    @Test fun `duplicate start does not extend ringing limit or publish twice`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        service.onStartCommand(startIntent(), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { manager.startAlarmForeground(any(), any()) }
        verify(exactly = 1) { playback.prepare(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
    }

    @Test fun `playback startup failure has a distinct final diagnostic without success`() {
        val service = createService()
        every { playback.play(any()) } answers {
            val attempt = attempts.last()
            Handler(Looper.getMainLooper()).post {
                attempt.event(PlaybackEvent.Failed("playback_start_failed", "IOException", true))
            }
            true
        }
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_start_failed", "IOException") }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `delayed preparation is not reported as playing and does not consume playback allowance`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        verify(exactly = 0) { playback.play(any()) }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
        attempts.single().prepared(1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29))
        assertFalse(shadowOf(service).isStoppedBySelf)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `preparation timeout stops and publishes fallback without accepting a late callback`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AlarmRingtoneService.PREPARATION_TIMEOUT_MS))
        attempts.single().prepared(1)
        attempts.single().event(PlaybackEvent.Started(SystemClock.elapsedRealtime(), 5_000))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { playback.stop() }
        verify(exactly = 0) { playback.play(any()) }
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_prepare_timeout") }
        verify(exactly = 1) { manager.publishPlaybackFallback(any()) }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
    }

    @Test fun `possibly stuck older playback suppresses a second audible fallback`() {
        autoPrepare = false
        every { playback.isAudibleFallbackSafe } returns false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { manager.publishPlaybackFallback(any()) }
    }

    @Test fun `rejected play authorization retains original five second timeout and safe fallback`() {
        every { playback.play(any()) } returns false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_prepare_timeout") }
        verify(exactly = 1) { manager.publishPlaybackFallback(any()) }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
    }

    @Test fun `authorized native start with no result is bounded by total service budget`() {
        autoStart = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(AlarmRingtoneService.TOTAL_TIMEOUT_MS))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { playback.stop() }
        verify(exactly = 0) { manager.publishPlaybackFallback(any()) }
    }

    @Test fun `logout while preparing prevents authorization and fallback`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        every { manager.ownsAlarm(any<CountdownAlarm>()) } returns false
        attempts.single().prepared(1)
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { playback.play(any()) }
        verify(exactly = 0) { manager.publishPlaybackFallback(any()) }
    }

    @Test fun `replacement ignores old callbacks without stopping newer preparation`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        val replacement = startIntent().apply { putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 5678L) }
        service.onStartCommand(replacement, 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        attempts.first().prepared(1)
        attempts.first().event(PlaybackEvent.Failed("playback_prepare_failed", "IOException", true))
        assertFalse(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { playback.stop() }
        verify(exactly = 0) { playback.play(any()) }
        attempts.last().prepared(2)
        verify(exactly = 1) { playback.play(2) }
        verify(exactly = 0) { manager.publishPlaybackFallback(any()) }
    }

    @Test fun `destroy while preparing rejects queued success and prepared callbacks`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        service.onDestroy()
        attempts.single().prepared(1)
        attempts.single().event(PlaybackEvent.Started(SystemClock.elapsedRealtime(), 0))
        verify(exactly = 1) { playback.stop() }
        verify(exactly = 0) { playback.play(any()) }
        verify(exactly = 0) { manager.trackDeliveryResult(any(), "playback_started", playbackStartupDurationMs = any()) }
    }

    @Test fun `asynchronous prepare error preserves notification and requests existing fallback only after release`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        attempts.single().event(PlaybackEvent.Failed("playback_prepare_failed", "MediaPlayerError(what=1,extra=-1004)", true))
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_prepare_failed", "MediaPlayerError(what=1,extra=-1004)") }
        verify(exactly = 1) { manager.publishPlaybackFallback(any()) }
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(shadowOf(service).notificationShouldRemoved)
    }

    @Test fun `prepare release failure still permits fallback when no playback was authorized or possible`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        attempts.single().event(PlaybackEvent.Failed("playback_prepare_failed", "IOException", false))
        verify(exactly = 1) { manager.publishPlaybackFallback(any()) }
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `failed native release never triggers another audible fallback`() {
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        attempts.single().event(PlaybackEvent.Failed("playback_failed", "MediaPlayerError(what=100,extra=0)", false))
        verify(exactly = 1) { manager.trackDeliveryResult(any(), "playback_failed", "MediaPlayerError(what=100,extra=0)") }
        verify(exactly = 0) { manager.publishPlaybackFallback(any()) }
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `duplicate during preparation does not postpone original preparation timeout`() {
        autoPrepare = false
        val service = createService()
        service.onStartCommand(startIntent(), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        service.onStartCommand(startIntent(), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(shadowOf(service).isStoppedBySelf)
        verify(exactly = 1) { playback.prepare(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { manager.publishPlaybackFallback(any()) }
    }

    @Test fun `stop uses stopService without creating a replacement service`() {
        AlarmRingtoneService.stopRingtone(context)
        val application = shadowOf(context as Application)
        assertNull(application.nextStartedService)
        assertEquals(AlarmRingtoneService::class.java.name, application.nextStoppedService.component?.className)
    }

    @Test
    @Config(sdk = [24, 28, 31, 36])
    fun `start dispatches once using the platform API and preserves original identity`() {
        var foregroundCalls = 0
        var legacyCalls = 0
        var received: Intent? = null
        val recordingContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName {
                foregroundCalls++
                received = service
                return ComponentName(context, AlarmRingtoneService::class.java)
            }
            override fun startService(service: Intent): ComponentName {
                legacyCalls++
                received = service
                return ComponentName(context, AlarmRingtoneService::class.java)
            }
        }

        AlarmRingtoneService.startRingtone(recordingContext, CountdownAlarm(orderKey, "护理", "1:2:3", 1234L))

        assertEquals(if (Build.VERSION.SDK_INT >= 26) 1 else 0, foregroundCalls)
        assertEquals(if (Build.VERSION.SDK_INT >= 26) 0 else 1, legacyCalls)
        assertEquals(orderKey, CountdownNotificationManager.extractOrderKey(received!!))
        assertEquals("1:2:3", received!!.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER))
        assertEquals(1234L, received!!.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
    }

    @Test
    @Config(sdk = [28, 31, 36])
    fun `native start exceptions propagate without ordinary service or activity workaround`() {
        val failure = SecurityException("start denied")
        val rejectedContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? = throw failure
            override fun startService(service: Intent): ComponentName? = error("Must not retry as ordinary service")
            override fun startActivity(intent: Intent) = error("Must not force an activity")
        }

        val caught = assertThrows(SecurityException::class.java) {
            AlarmRingtoneService.startRingtone(rejectedContext, CountdownAlarm(orderKey, "护理", "1:2:3", 1234L))
        }
        assertSame(failure, caught)
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
