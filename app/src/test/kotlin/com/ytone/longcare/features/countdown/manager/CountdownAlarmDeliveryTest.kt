package com.ytone.longcare.features.countdown.manager

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.countdown.service.AlarmRingtonePlaybackController
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.User
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownAlarmDeliveryTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    private lateinit var context: Context
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager
    private val order = OrderKey(12345L, 12)
    private val session = MutableStateFlow<SessionState>(SessionState.LoggedIn(
        User(companyId = 1, accountId = 2, userId = 3)))
    private val orderState = MutableStateFlow<ServiceOrderStateModel?>(null)
    private lateinit var manager: CountdownNotificationManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("countdown_notification_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33) {
            shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        alarms = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        notifications = mockk(relaxed = true)
        mockkObject(AlarmRingtoneService.Companion)
        every { AlarmRingtoneService.startRingtone(any(), any()) } just runs
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `normal alarm delegates once to service without publishing a second notification`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()

        manager.onAlarmTriggered(intent)

        verify(exactly = 1) { AlarmRingtoneService.startRingtone(context, manager.extractAlarm(intent)!!) }
        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test fun `confirmed terminal state prevents delivery before the cleanup observer runs`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        manager.startSessionObservation()
        for (terminal in listOf(ServiceOrderStateModel.STATE_COMPLETED, ServiceOrderStateModel.STATE_CANCELLED)) {
            orderState.value = ServiceOrderStateModel(order.orderId, terminal)

            manager.onAlarmTriggered(intent)

            assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        }
        verify(exactly = 0) { AlarmRingtoneService.startRingtone(any(), any()) }
        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test
    @Config(sdk = [31, 33, 34, 35, 36])
    fun `background service rejection publishes a system notification with original identity`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws
            ForegroundServiceStartNotAllowedException("background start denied")
        val published = slot<Notification>()
        every { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, capture(published)) } just runs

        manager.onAlarmTriggered(intent)

        verify(exactly = 1) { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, any<Notification>()) }
        assertEquals(CountdownNotificationManager.COUNTDOWN_FALLBACK_CHANNEL_ID, published.captured.channelId)
        assertNotNull(published.captured.fullScreenIntent)
        val shown = shadowOf(published.captured.contentIntent).savedIntent
        assertEquals(order, CountdownNotificationManager.extractOrderKey(shown))
        assertEquals(intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L),
            shown.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    @Config(sdk = [28, 31, 34, 36])
    fun `nullable native foreground start publishes audible fallback with original ownership`() = runTest {
        unmockkObject(AlarmRingtoneService.Companion)
        var startCalls = 0
        context = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                startCalls++
                return null
            }
            override fun startService(service: Intent): ComponentName? = error("Must not retry as ordinary service")
        }
        createManager(backgroundScope)
        val intent = schedule()
        val published = slot<Notification>()
        every { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, capture(published)) } just runs

        manager.onAlarmTriggered(intent)

        assertEquals(1, startCalls)
        assertEquals(CountdownNotificationManager.COUNTDOWN_FALLBACK_CHANNEL_ID, published.captured.channelId)
        val contentIntent = shadowOf(published.captured.contentIntent).savedIntent
        assertEquals(order, CountdownNotificationManager.extractOrderKey(contentIntent))
        assertEquals(intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L),
            contentIntent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertNull(shadowOf(context.applicationContext as Application).nextStartedActivity)
    }

    @Test
    @Config(sdk = [28, 34])
    fun `a blocked original countdown channel is not bypassed by audible fallback`() = runTest {
        val systemNotifications = context.getSystemService(NotificationManager::class.java)
        systemNotifications.createNotificationChannel(android.app.NotificationChannel(
            CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID, "blocked", NotificationManager.IMPORTANCE_NONE))
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws SecurityException("start denied")

        manager.onAlarmTriggered(intent)

        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test fun `a blocked reminder channel group is not bypassed by fallback`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws SecurityException("start denied")
        val compat = mockk<NotificationManagerCompat>(relaxed = true)
        val group = mockk<androidx.core.app.NotificationChannelGroupCompat>()
        every { group.isBlocked } returns true
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns compat
        every { compat.areNotificationsEnabled() } returns true
        every { compat.getNotificationChannelCompat(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID) } returns
            androidx.core.app.NotificationChannelCompat.Builder("countdown", NotificationManager.IMPORTANCE_HIGH)
                .setName("countdown").setGroup("blocked_group").build()
        every { compat.getNotificationChannelGroupCompat("blocked_group") } returns group

        manager.onAlarmTriggered(intent)

        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test fun `foreground promotion rejection retains fallback and releases playback resources`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        val published = slot<Notification>()
        every { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, capture(published)) } just runs
        val controller = Robolectric.buildService(AlarmRingtoneService::class.java)
        val service = controller.get()
        ReflectionHelpers.setField(service, "injected", true)
        service.countdownNotificationManager = manager
        controller.create()
        val playback = mockk<AlarmRingtonePlaybackController>(relaxed = true)
        ReflectionHelpers.setField(service, "playbackController", playback)
        try {
            shadowOf(service).setThrowInStartForeground(SecurityException("promotion denied"))

            service.onStartCommand(intent, 0, 1)

            verify(exactly = 1) { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, any<Notification>()) }
            assertEquals(CountdownNotificationManager.COUNTDOWN_FALLBACK_CHANNEL_ID, published.captured.channelId)
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(ReflectionHelpers.getField<PowerManager.WakeLock>(service, "wakeLock").isHeld)
            verify(exactly = 0) { playback.start() }
            verify(exactly = 1) { playback.stop() }
        } finally {
            controller.destroy()
        }
    }

    @Test
    @Config(sdk = [28, 34])
    fun `silently ignored foreground promotion cannot start media playback`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        val controller = Robolectric.buildService(AlarmRingtoneService::class.java)
        val service = controller.get()
        ReflectionHelpers.setField(service, "injected", true)
        service.countdownNotificationManager = manager
        controller.create()
        val playback = mockk<AlarmRingtonePlaybackController>(relaxed = true)
        ReflectionHelpers.setField(service, "playbackController", playback)
        mockkStatic(ServiceCompat::class)
        every { ServiceCompat.startForeground(any(), any(), any(), any()) } just runs
        try {
            service.onStartCommand(intent, 0, 1)

            verify(exactly = 0) { playback.start() }
            verify(exactly = 1) { playback.stop() }
            verify(exactly = 1) { notifications.notify(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID, any<Notification>()) }
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(ReflectionHelpers.getField<PowerManager.WakeLock>(service, "wakeLock").isHeld)
        } finally {
            controller.destroy()
        }
    }

    @Test fun `same order reschedule never grants a stale intent the new identity`() = runTest {
        createManager(backgroundScope)
        val stale = schedule()
        schedule(extraDelay = 30_000L)

        manager.onAlarmTriggered(stale)
        val controller = Robolectric.buildService(TestForegroundService::class.java).create()
        try {
            assertFalse(manager.startAlarmForeground(controller.get(), manager.extractAlarm(stale)!!))
            assertNull(shadowOf(controller.get()).lastForegroundNotification)
        } finally {
            controller.destroy()
        }
        verify(exactly = 0) { AlarmRingtoneService.startRingtone(any(), any()) }
        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test fun `ownership lost during failed start cannot publish a cancelled reminder`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } answers {
            manager.cancelCountdownAlarmForOrder(order)
            throw ForegroundServiceStartNotAllowedException("start denied after cancellation")
        }

        manager.onAlarmTriggered(intent)

        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test
    @Config(sdk = [33, 34, 35, 36])
    fun `notification denial prevents fallback publication but does not block a legal FGS`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        val alarm = manager.extractAlarm(intent)!!
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws
            ForegroundServiceStartNotAllowedException("background start denied")

        manager.onAlarmTriggered(intent)
        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }

        val controller = Robolectric.buildService(TestForegroundService::class.java).create()
        try {
            assertTrue(manager.startAlarmForeground(controller.get(), alarm))
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
            assertEquals(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID,
                shadowOf(controller.get()).lastForegroundNotification.channelId)
            assertNull(shadowOf(controller.get()).lastForegroundNotification.sound)
        } finally {
            controller.destroy()
        }
    }

    @Test fun `session change during notification construction prevents publication`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws
            ForegroundServiceStartNotAllowedException("background start denied")
        val compat = mockk<NotificationManagerCompat>(relaxed = true)
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns compat
        every { compat.areNotificationsEnabled() } returns true
        every { compat.getNotificationChannelCompat(any()) } returns null
        every { compat.canUseFullScreenIntent() } answers {
            session.value = SessionState.LoggedOut
            false
        }

        manager.onAlarmTriggered(intent)

        verify(exactly = 0) { notifications.notify(any<Int>(), any<Notification>()) }
    }

    @Test fun `notification publication failure is contained without launching UI`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        every { AlarmRingtoneService.startRingtone(any(), any()) } throws SecurityException("start failed")
        every { notifications.notify(any<Int>(), any<Notification>()) } throws SecurityException("notification failed")

        manager.onAlarmTriggered(intent)

        verify(exactly = 1) { notifications.notify(any<Int>(), any<Notification>()) }
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    @Config(sdk = [24, 28, 30, 31, 34, 36])
    fun `visible alarm clock is used on every supported API with exact alarm capability`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        val fields = CountdownAlarmDiagnostics.fields(CountdownAlarmDiagnostics.read(intent),
            intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L), "scheduled")
        assertEquals("alarm_clock", fields["scheduleMode"])
        assertEquals(intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L),
            alarms.nextAlarmClock?.triggerTime)
        assertNull(fields["fallback"])
        assertNull(fields["receiverDelayMs"])
    }

    @Test
    @Config(sdk = [28, 30])
    fun `pre Android twelve alarm clock does not require newer exact alarm permission`() {
        scheduleCountdownAlarmInSystem(context, alarms, 3001, "COUNTDOWN_ALARM_", order,
            "护理", System.currentTimeMillis() + 60_000L, canUseExactAlarm = false, owner = "1:2:3")
        val intent = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        val fields = CountdownAlarmDiagnostics.fields(CountdownAlarmDiagnostics.read(intent),
            intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L), "scheduled")
        assertEquals("alarm_clock", fields["scheduleMode"])
        assertEquals(true, fields["canScheduleExact"])
        assertNull(fields["fallback"])
    }

    @Test fun `inexact permission fallback remains visible after Intent parcel round trip`() = runTest {
        createManager(backgroundScope)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val intent = schedule()
        val parcel = Parcel.obtain()
        val restored = try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
        val fields = CountdownAlarmDiagnostics.fields(CountdownAlarmDiagnostics.read(restored),
            restored.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L), "delivered")
        assertEquals("inexact_allow_idle", fields["scheduleMode"])
        assertEquals("exact_permission_missing", fields["fallback"])
    }

    @Test
    @Config(shadows = [PermissionRevokedAlarmManager::class])
    fun `permission revoked during exact schedule updates delivered fallback diagnostics`() {
        scheduleCountdownAlarmInSystem(context, alarms, 3001, "COUNTDOWN_ALARM_", order,
            "护理", System.currentTimeMillis() + 60_000L, canUseExactAlarm = true, owner = "1:2:3")
        val intent = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        val fields = CountdownAlarmDiagnostics.fields(CountdownAlarmDiagnostics.read(intent),
            intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L), "delivered")
        assertEquals("inexact_allow_idle", fields["scheduleMode"])
        assertEquals("exact_permission_revoked", fields["fallback"])
        assertEquals(true, fields["canScheduleExact"])
    }

    @Test fun `manager validation preserves the receiver entry timestamp`() {
        val intent = Intent()
        CountdownAlarmDiagnostics.receiverEntered(intent, context)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3))
        CountdownAlarmDiagnostics.receiverEntered(intent, context)
        CountdownAlarmDiagnostics.validated(intent)
        assertEquals(3_000L, CountdownAlarmDiagnostics.fields(
            CountdownAlarmDiagnostics.read(intent), 0L, "validated")["validationMs"])
    }

    @Test fun `clock change and sleep are separated from receiver and validation delay`() {
        // Device slept 80 s, wall clock advanced an extra 20 s; validation itself took 3 s.
        val data = Bundle().apply {
            putLong("scheduleWall", 1_000_000L); putLong("scheduleElapsed", 10_000L)
            putLong("scheduleUptime", 5_000L); putLong("receiverWall", 1_140_000L)
            putLong("receiverElapsed", 130_000L); putLong("receiverUptime", 45_000L)
            putLong("validatedElapsed", 133_000L); putLong("serviceElapsed", 133_500L)
        }
        val fields = CountdownAlarmDiagnostics.fields(data, 1_060_000L, "playback_started")
        assertEquals(80_000L, fields["receiverDelayMs"])
        assertEquals(20_000L, fields["clockShiftMs"])
        assertEquals(80_000L, fields["sleepMs"])
        assertEquals(3_000L, fields["validationMs"])
        assertEquals(500L, fields["serviceDispatchMs"])
        assertNull(CountdownAlarmDiagnostics.fields(null, 0L, "legacy")["receiverDelayMs"])
    }

    @Test fun `rejected delivery reports captured account with the validation cause once`() = runTest {
        createManager(backgroundScope)
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "3"
        every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } just runs
        val intent = schedule()
        session.value = SessionState.LoggedOut

        manager.onAlarmTriggered(intent)

        verify(exactly = 1) { DiagnosticEventTracker.trackEvent(
            DiagnosticCategory.COUNTDOWN, "alarm_delivery_result", any(),
            match { it["outcome"] == "validation_rejected" && it["validationFailure"] == "session_unresolved_or_logged_out" },
            "3", true) }
        verify(exactly = 0) { AlarmRingtoneService.startRingtone(any(), any()) }
    }

    @Test fun `accepted identity is not replayed when clock rollback makes its deadline future again`() = runTest {
        createManager(backgroundScope)
        val intent = schedule()
        val trigger = intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L)
        alarms.cancel(shadowOf(alarms).scheduledAlarms.single().operation)
        manager.onAlarmTriggered(intent)
        manager.onAlarmTriggered(intent)
        // A future deadline plus accepted delivery is also the post-rollback persistent state.
        createManager(backgroundScope)
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        manager.scheduleCountdownAlarm(order, "护理", trigger)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        verify(exactly = 1) { AlarmRingtoneService.startRingtone(any(), any()) }

        val replacement = schedule(extraDelay = 30_000L)
        manager.onAlarmTriggered(replacement)
        verify(exactly = 2) { AlarmRingtoneService.startRingtone(any(), any()) }
        manager.scheduleCountdownAlarm(OrderKey(678L, 3), "新订单", trigger + 90_000L)
        val newOrder = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        manager.onAlarmTriggered(newOrder)
        verify(exactly = 3) { AlarmRingtoneService.startRingtone(any(), any()) }
    }

    private fun createManager(scope: kotlinx.coroutines.CoroutineScope) {
        val repository = mockk<UserSessionRepository> { every { sessionState } returns session }
        val lifecycle = mockk<ServiceOrderLifecycle> {
            every { orderState } returns this@CountdownAlarmDeliveryTest.orderState
        }
        manager = CountdownNotificationManager(context, notifications, alarms, repository,
            mockk<LocationRecoveryStore>(), mockk<OrderRepository>(), lifecycle, scope)
    }

    private fun schedule(extraDelay: Long = 0L): Intent {
        manager.scheduleCountdownAlarm(order, "护理", System.currentTimeMillis() + 60_000L + extraDelay)
        return Intent(shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent)
    }

    @Implements(AlarmManager::class)
    class PermissionRevokedAlarmManager : ShadowAlarmManager() {
        @Implementation
        override fun setAlarmClock(info: AlarmManager.AlarmClockInfo, operation: PendingIntent) {
            throw SecurityException("revoked after capability check")
        }
    }

    class TestForegroundService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }
}
