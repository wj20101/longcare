package com.ytone.longcare.features.servicecountdown.ui

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.app.MainApplication
import com.ytone.longcare.data.time.AndroidServiceTimeProvider
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.countdown.testing.CountdownDeviceTestEntryPoint
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.service.ServiceCountdownSystemGatewayImpl
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.ServiceCountdownDeadline
import com.ytone.longcare.model.ServiceTimeSnapshot
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.NavigationStateTestActivity
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Real Activity/Compose lifecycle regression with isolated order data and a recording gateway.
 * Default tests never change accounts, permissions or system alarms. The explicitly enabled real
 * alarm tests use a fake order and may create a temporary local session only on an empty device.
 */
class ServiceCountdownBackgroundInitializationTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private var hostWasBackgrounded = false
    private var realAlarmManager: CountdownNotificationManager? = null
    private var createdSession: UserSessionRepository? = null
    private var alarmObserver: AlarmActivityObserver? = null
    private var lifecycleTestWakeLock: PowerManager.WakeLock? = null

    @After
    fun restoreHostAndReleaseViewModel() {
        try {
            instrumentation.runOnMainSync { store.clear() }
            realAlarmManager?.cancelCountdownAlarmForOrder(TEST_ORDER)
        } finally {
            try {
                alarmObserver?.let { observer ->
                    (instrumentation.targetContext.applicationContext as Application).unregisterActivityLifecycleCallbacks(observer)
                }
                createdSession?.let { session ->
                    runBlocking {
                        // Never log out a real account that appeared while the opt-in test was running.
                        val current = session.sessionState.value
                        if (current !is SessionState.LoggedOut) {
                            check(current.user == TEST_USER) { "Temporary test account changed; refusing to log out another account" }
                            session.logout()
                        }
                    }
                }
            } finally {
                lifecycleTestWakeLock?.let { if (it.isHeld) it.release() }
                if (hostWasBackgrounded) {
                    // Restore the test host, without changing screen timeout or lock settings.
                    // dismiss-keyguard cannot bypass a secure PIN/password lock.
                    restoreHost()
                }
            }
        }
    }

    @Test
    fun wait_for_accepts_observed_success_once_and_still_rejects_a_missed_deadline() {
        var reads = 0
        waitFor("the first successful observation must be accepted", timeoutMillis = 0L) {
            ++reads == 1 // A second read would see a different transient lifecycle state.
        }
        assertEquals("success must not be re-read after leaving the polling loop", 1, reads)
        val error = assertThrows(AssertionError::class.java) {
            waitFor("condition never became true", timeoutMillis = 0L) { false }
        }
        assertEquals("condition never became true", error.message)
    }

    @Test
    fun immediate_initial_read_schedules_once_on_first_resume() {
        val details = BlockingOrderDetails(TEST_ORDER, ReadBoundary.ORDER_INFO)
        details.releaseRead.complete(Unit)
        val gateway = RecordingGateway()
        renderCountdown(details, gateway)
        waitFor("initial reminder was not scheduled") { gateway.scheduled.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, gateway.scheduled.size)
        assertEquals(1, details.orderReads)
        assertEquals(1, details.localReads)
    }

    @Test
    fun home_during_order_read_still_schedules_without_background_display_updates() {
        verifyBackgroundInitialization(ReadBoundary.ORDER_INFO, lockScreen = false)
    }

    @Test
    fun home_during_local_start_read_still_schedules_the_original_deadline() {
        verifyBackgroundInitialization(ReadBoundary.LOCAL_START, lockScreen = false)
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun lock_screen_during_order_read_still_schedules_without_background_display_updates() {
        verifyBackgroundInitialization(ReadBoundary.ORDER_INFO, lockScreen = true)
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun lock_screen_during_local_start_read_still_schedules_the_original_deadline() {
        verifyBackgroundInitialization(ReadBoundary.LOCAL_START, lockScreen = true)
    }

    /** Run with -e runRealAlarm true; an empty device also requires initializeEmptyDevice=true. */
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun real_alarm_after_home_delivers_sound_and_retains_notification_after_timeout() {
        verifyRealAlarmDelivery(lockScreen = false)
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun real_alarm_after_lock_screen_automatically_opens_the_original_alarm_and_stops_sound() {
        verifyRealAlarmDelivery(lockScreen = true)
    }

    private fun verifyRealAlarmDelivery(lockScreen: Boolean) {
        assumeTrue("Real sound/lock-screen tests require explicit runRealAlarm=true",
            InstrumentationRegistry.getArguments().getString("runRealAlarm") == "true")
        val app = instrumentation.targetContext.applicationContext as MainApplication
        val manager = app.countdownNotificationManager
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        val audioManager = app.getSystemService(AudioManager::class.java)
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val dependencies = EntryPointAccessors.fromApplication(app, CountdownDeviceTestEntryPoint::class.java)
        val session = dependencies.userSessionRepository()
        val prefs = app.getSharedPreferences("countdown_notification_prefs", Context.MODE_PRIVATE)
        check(!prefs.contains("key_last_scheduled_order_id")) { "Refusing to replace an existing order alarm" }
        check(notificationManager.activeNotifications.none { it.id == CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID }) {
            "Refusing to replace an existing countdown notification"
        }
        runBlocking {
            check(dependencies.locationRecoveryStore().read() == null) { "Refusing to test while an order has location recovery state" }
            val state = withTimeout(10_000L) { session.sessionState.first { it !is SessionState.Unknown } }
            if (state is SessionState.LoggedOut) {
                check(InstrumentationRegistry.getArguments().getString("initializeEmptyDevice") == "true") {
                    "Empty device requires initializeEmptyDevice=true to create an offline temporary test session"
                }
                createdSession = session
                session.login(TEST_USER)
            }
            check(manager.awaitResolvedSession()) { "A resolved local session is required for real alarm ownership" }
        }
        check(manager.canScheduleExactAlarms()) { "Enable exact alarms before running the timed real-alarm test" }
        check(notificationManager.areNotificationsEnabled()) { "Enable app notifications before running the real-alarm test" }
        check(notificationManager.getNotificationChannel(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID)
            ?.importance?.let { it >= NotificationManager.IMPORTANCE_HIGH } == true) {
            "The countdown reminder channel must be enabled at high importance"
        }
        if (lockScreen) check(manager.canUseFullScreenIntent()) { "Enable full-screen reminders before testing automatic lock-screen presentation" }
        check(audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0) { "Alarm volume must be audible for the device test" }
        check(!hasAlarmPlayback(audioManager)) { "Another alarm is already playing; refusing to use it as test evidence" }
        realAlarmManager = manager
        val observer = AlarmActivityObserver(TEST_ORDER)
        alarmObserver = observer
        app.registerActivityLifecycleCallbacks(observer)

        val details = BlockingOrderDetails(TEST_ORDER,
            if (lockScreen) ReadBoundary.LOCAL_START else ReadBoundary.ORDER_INFO,
            deadline = System.currentTimeMillis() + 20_000L)
        val gateway = RecordingGateway(ServiceCountdownSystemGatewayImpl(manager))
        renderCountdown(details, gateway)
        waitFor("first real-alarm preparation did not reach the blocked read") { details.readEntered.isCompleted }
        backgroundHost(lockScreen)
        details.releaseRead.complete(Unit)
        waitFor("real reminder was not scheduled after leaving the page") { gateway.scheduled.isNotEmpty() }
        assertFalse(details.readCancelled)
        assertEquals(1, gateway.scheduled.size)
        assertEquals(ScheduledAlarm(TEST_ORDER, SERVICE_NAME, details.deadline), gateway.scheduled.single())
        assertEquals(0, details.localStarts)
        assertEquals("the system schedule must keep the persisted deadline", details.deadline, prefs.getLong("trigger_time", 0L))
        assertEquals("the monotonic deadline must reach the system scheduler", details.monotonicDeadline,
            prefs.getLong("elapsed_deadline", -1L))
        assertEquals(details.bootCount, prefs.getInt("deadline_boot", -1))
        instrumentation.sendStatus(0, Bundle().apply {
            putString("realAlarmStage", "scheduled_in_background")
            putString("realAlarmMode", if (lockScreen) "lock_screen" else "home")
            putLong("realAlarmTriggerAt", details.deadline)
        })

        fun hasTestNotification() = notificationManager.activeNotifications.any {
            it.id == CountdownNotificationManager.COUNTDOWN_NOTIFICATION_ID &&
                it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.contains(SERVICE_NAME) == true &&
                it.notification.contentIntent != null
        }
        @Suppress("DEPRECATION")
        fun ringtoneServiceRunning() = activityManager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == AlarmRingtoneService::class.java.name && it.foreground
        }
        waitFor("system alarm did not start the real foreground ringtone service and audio", timeoutMillis = 35_000L) {
            hasTestNotification() && ringtoneServiceRunning() && hasAlarmPlayback(audioManager)
        }
        val playbackObservedAt = SystemClock.elapsedRealtime()
        assertTrue("alarm fired before its persisted monotonic deadline", playbackObservedAt >= details.monotonicDeadline)
        if (lockScreen) {
            waitFor("the system did not automatically open the original alarm Activity while locked") { observer.resumed }
            assertEquals(TEST_ORDER, observer.deliveredOrder)
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("realAlarmStage", "ringing")
            putString("realAlarmMode", if (lockScreen) "lock_screen" else "home")
            putBoolean("realAlarmActivityResumed", observer.resumed)
            putLong("realAlarmPlaybackObservedAt", playbackObservedAt)
            putLong("realAlarmObservedDelayMs", playbackObservedAt - details.monotonicDeadline)
        })
        waitFor("alarm did not stop its audio and foreground service automatically", timeoutMillis = 40_000L) {
            !hasAlarmPlayback(audioManager) && !ringtoneServiceRunning()
        }
        val playbackDuration = SystemClock.elapsedRealtime() - playbackObservedAt
        assertTrue("ringtone stopped before the expected 30-second budget: $playbackDuration", playbackDuration >= 28_000L)
        assertTrue("ringtone exceeded its bounded playback budget: $playbackDuration", playbackDuration <= 38_000L)
        assertTrue("notification must remain actionable after automatic sound timeout", hasTestNotification())
        instrumentation.sendStatus(0, Bundle().apply {
            putString("realAlarmStage", "automatic_timeout_verified")
            putString("realAlarmMode", if (lockScreen) "lock_screen" else "home")
            putLong("realAlarmObservedPlaybackDuration", playbackDuration)
            putBoolean("realAlarmNotificationRetained", true)
            putBoolean("realAlarmActivityResumed", observer.resumed)
        })
    }

    private fun verifyBackgroundInitialization(boundary: ReadBoundary, lockScreen: Boolean) {
        if (lockScreen) {
            // Fake Gateway 不启动真实前台服务；只为测试断言保持 CPU，真实闹钟用例不使用此锁。
            val power = instrumentation.targetContext.getSystemService(PowerManager::class.java)
            lifecycleTestWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "LongCare:CountdownLifecycleTest").apply { acquire(90_000L) }
        }
        val order = TEST_ORDER
        val details = BlockingOrderDetails(order, boundary)
        val gateway = RecordingGateway()
        val viewModel = renderCountdown(details, gateway)
        waitFor("first countdown read did not start") { details.readEntered.isCompleted }
        assertTrue(hostIsResumed())

        backgroundHost(lockScreen)

        details.releaseRead.complete(Unit)
        waitFor("background initialization did not schedule the reminder") { gateway.scheduled.isNotEmpty() }
        assertFalse("Home/lock must not cancel business initialization", details.readCancelled)
        assertEquals(1, gateway.scheduled.size)
        assertEquals(ScheduledAlarm(order, SERVICE_NAME, details.deadline), gateway.scheduled.single())
        assertEquals("persisted service start must be reused", 0, details.localStarts)
        if (lockScreen) assertFalse("preparation must finish while the screen is still off",
            instrumentation.targetContext.getSystemService(PowerManager::class.java).isInteractive)

        // A background completion may update the display once. It must not restart its one-second ticker.
        val backgroundTime = viewModel.formattedTime.value
        SystemClock.sleep(2_100L)
        assertEquals("background countdown display must remain paused", backgroundTime, viewModel.formattedTime.value)
        assertEquals(1, gateway.scheduled.size)
        assertEquals(1, details.orderReads)
        assertEquals(1, details.localReads)

        restoreHost()
        waitFor("resuming did not refresh the countdown display") { viewModel.formattedTime.value != backgroundTime }
        waitFor("resuming did not refresh the reminder permission state") { gateway.scheduled.size >= 2 }
        assertTrue(gateway.scheduled.all { it == ScheduledAlarm(order, SERVICE_NAME, details.deadline) })
        assertEquals(0, details.localStarts)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("countdownReadBoundary", boundary.name)
            putString("countdownBackgroundMode", if (lockScreen) "lock_screen" else "home")
            putLong("countdownDeadline", details.deadline)
            putInt("countdownSchedules", gateway.scheduled.size)
            putBoolean("countdownReadCancelled", details.readCancelled)
        })
    }

    private fun renderCountdown(details: BlockingOrderDetails, gateway: RecordingGateway): ServiceCountdownViewModel {
        lateinit var viewModel: ServiceCountdownViewModel
        instrumentation.runOnMainSync {
            viewModel = ServiceCountdownViewModel(details, NoImages, NoOrderMonitoring, gateway, testTimeProvider)
            store.put("countdown", viewModel)
        }
        compose.setContent {
            ServiceCountdownTimerLifecycleEffect(viewModel, TEST_ORDER, listOf(PROJECT_ID), compose.activity)
        }
        return viewModel
    }

    private fun backgroundHost(lockScreen: Boolean) {
        hostWasBackgrounded = true
        assertTrue("Home action was rejected", instrumentation.uiAutomation.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_HOME))
        if (lockScreen) {
            assertTrue("Lock-screen action was rejected", instrumentation.uiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
        }
        waitFor("host did not leave RESUMED") {
            !hostIsResumed()
        }
        if (lockScreen) {
            val power = instrumentation.targetContext.getSystemService(PowerManager::class.java)
            waitFor("lock-screen action did not turn the screen off") { !power.isInteractive }
        }
    }

    private fun hasAlarmPlayback(audioManager: AudioManager): Boolean =
        audioManager.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }

    private fun restoreHost() {
        // The body may already have restored this host. Reordering an already resumed Activity
        // delivers another intent and can introduce an unnecessary PAUSED -> RESUMED transition.
        if (!hostIsResumed()) {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            instrumentation.runOnMainSync {
                val host = compose.activity
                if (!host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    host.startActivity(Intent(host, NavigationStateTestActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
            }
            waitFor("test host did not return; unlock the test device if it has a secure keyguard", 60_000L) {
                hostIsResumed()
            }
        }
        hostWasBackgrounded = false
    }

    private fun hostIsResumed(): Boolean {
        var resumed = false
        instrumentation.runOnMainSync {
            resumed = compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        return resumed
    }

    private fun waitFor(message: String, timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            // A successful observation completes the wait. Re-reading here can spuriously fail
            // if a lifecycle state changes between the loop condition and a final assertion.
            if (condition()) return
            if (SystemClock.uptimeMillis() >= deadline) break
            SystemClock.sleep(50L)
        }
        fail(message)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    }

    private enum class ReadBoundary { ORDER_INFO, LOCAL_START }

    private class BlockingOrderDetails(
        private val order: OrderKey,
        private val boundary: ReadBoundary,
        val deadline: Long = System.currentTimeMillis() + 50_000L,
    ) : OrderDetailRepository {
        private val referenceTime = testTimeProvider.snapshot()
        val bootCount = requireNotNull(referenceTime.bootCount) { "Device tests require a known boot identity" }
        val monotonicDeadline = referenceTime.elapsedRealtimeMillis + deadline - referenceTime.wallTimeMillis
        private val startedAt = deadline - TimeUnit.MINUTES.toMillis(1L)
        val readEntered = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        @Volatile var readCancelled = false
        @Volatile var orderReads = 0
        @Volatile var localReads = 0
        @Volatile var localStarts = 0

        override suspend fun getOrderInfo(orderKey: OrderKey, forceRefresh: Boolean): ApiResult<ServiceOrderInfoModel> {
            check(orderKey == order)
            orderReads++
            awaitRead(ReadBoundary.ORDER_INFO)
            return ApiResult.Success(ServiceOrderInfoModel(orderId = order.orderId,
                projectList = listOf(ServiceProjectM(projectId = PROJECT_ID, projectName = SERVICE_NAME, serviceTime = 1))))
        }

        override suspend fun getLocalState(orderKey: OrderKey): OrderLocalStateEntity {
            check(orderKey == order)
            localReads++
            awaitRead(ReadBoundary.LOCAL_START)
            return OrderLocalStateEntity(orderId = order.orderId, localStartTimestamp = startedAt,
                localStartElapsedRealtime = monotonicDeadline - TimeUnit.MINUTES.toMillis(1L),
                localStartBootCount = bootCount)
        }

        private suspend fun awaitRead(current: ReadBoundary) {
            if (current != boundary) return
            readEntered.complete(Unit)
            try {
                releaseRead.await()
            } catch (cancelled: CancellationException) {
                readCancelled = true
                throw cancelled
            }
        }

        override suspend fun startLocalService(orderKey: OrderKey, startedAt: ServiceTimeSnapshot) { localStarts++ }
        override fun getCachedOrderInfo(orderKey: OrderKey): ServiceOrderInfoModel? = null
        override suspend fun getSelectedProjectIds(orderKey: OrderKey) = listOf(PROJECT_ID)
        override suspend fun updateSelectedProjects(orderKey: OrderKey, selectedProjectIds: List<Int>) = Unit
        override suspend fun endLocalService(orderKey: OrderKey) = Unit
        override suspend fun updateFaceVerification(orderKey: OrderKey, completed: Boolean) = Unit
    }

    private data class ScheduledAlarm(val order: OrderKey, val serviceName: String, val deadline: Long)

    private class RecordingGateway(private val realGateway: ServiceCountdownSystemGateway? = null) : ServiceCountdownSystemGateway {
        val scheduled = CopyOnWriteArrayList<ScheduledAlarm>()
        override fun canScheduleExactAlarms() = true
        override fun canUseFullScreenIntent() = true
        override fun scheduleCountdownAlarm(orderKey: OrderKey, serviceName: String, deadline: ServiceCountdownDeadline) {
            realGateway?.scheduleCountdownAlarm(orderKey, serviceName, deadline)
            scheduled += ScheduledAlarm(orderKey, serviceName, deadline.wallTimeMillis)
        }
        override fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
            realGateway?.cancelCountdownAlarmForOrder(orderKey)
        }
    }

    private class AlarmActivityObserver(private val expectedOrder: OrderKey) : Application.ActivityLifecycleCallbacks {
        @Volatile var resumed = false
        @Volatile var deliveredOrder: OrderKey? = null
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) {
            if (activity is CountdownAlarmActivity) {
                deliveredOrder = CountdownNotificationManager.extractOrderKey(activity.intent)
                resumed = deliveredOrder == expectedOrder
            }
        }
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private object NoOrderMonitoring : ServiceOrderLifecycle {
        override val orderState = MutableStateFlow<ServiceOrderStateModel?>(null)
        override fun monitorOrder(orderId: Long) = Unit
        override fun prepareOrderStart(orderId: Long) = Unit
        override fun cancelOrderPreparation(orderId: Long) = Unit
        override fun onOrderStarted(orderId: Long) = Unit
        override fun onOrderEnded(orderId: Long) = Unit
    }

    private object NoImages : OrderImageRepository {
        override suspend fun getImagesByOrderId(orderKey: OrderKey) = emptyList<OrderImageEntity>()
        override suspend fun addImage(orderKey: OrderKey, imageType: ImageType, localUri: String, localPath: String?): Long =
            error("countdown lifecycle must not upload images")
        override suspend fun markAsSuccess(imageId: Long, cloudKey: String) = Unit
        override suspend fun deleteImage(imageId: Long) = Unit
        override suspend fun deleteImagesByOrderId(orderKey: OrderKey) = Unit
    }

    private companion object {
        val testTimeProvider by lazy {
            AndroidServiceTimeProvider(InstrumentationRegistry.getInstrumentation().targetContext)
        }
        const val PROJECT_ID = 1
        const val SERVICE_NAME = "后台初始化回归"
        val TEST_ORDER = OrderKey(orderId = 9_870_163L, planId = 1)
        val TEST_USER = User(companyId = 991, accountId = 992, userId = 993,
            userName = "倒计时本地验证", token = "")
    }
}
