package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.MainActivity
import com.ytone.longcare.util.MainDispatcherRule
import org.junit.Rule
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.domain.time.ServiceTimeProvider
import com.ytone.longcare.model.ServiceCountdownDeadline
import com.ytone.longcare.model.ServiceTimeSnapshot
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CountdownAlarmOwnershipTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    private var clock: ServiceTimeSnapshot? = null
    private val timeProvider = ServiceTimeProvider {
        clock ?: ServiceTimeSnapshot(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), 7)
    }
    private lateinit var context: Context
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager
    private val user = User(companyId = 1, accountId = 2, userId = 3)
    private val owner = "1:2:3"
    private val firstOrder = OrderKey(123L, 4)
    private val secondOrder = OrderKey(456L, 5)
    private lateinit var session: MutableStateFlow<SessionState>
    private lateinit var repository: UserSessionRepository
    private lateinit var recovery: LocationRecoveryStore
    private lateinit var orders: OrderRepository
    private lateinit var lifecycle: ServiceOrderLifecycle
    private lateinit var orderState: MutableStateFlow<ServiceOrderStateModel?>

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("countdown_notification_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        alarms = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        session = MutableStateFlow(SessionState.LoggedIn(user))
        repository = mockk { every { sessionState } returns session }
        recovery = mockk { coEvery { read() } returns null }
        orders = mockk()
        orderState = MutableStateFlow(null)
        lifecycle = mockk()
        every { lifecycle.orderState } returns orderState
    }

    @Test fun `replacing order rejects old dismiss and old alarm broadcast`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.scheduleCountdownAlarm(firstOrder, "旧订单", System.currentTimeMillis() + 60_000)
        val oldAlarm = alarmIntent(firstOrder)
        manager.scheduleCountdownAlarm(secondOrder, "新订单", System.currentTimeMillis() + 120_000)
        assertFalse(manager.ownsAlarm(oldAlarm))
        manager.dismissAlarm(firstOrder, owner, oldAlarm.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertTrue(manager.ownsAlarm(alarmIntent(secondOrder)))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        manager.dismissAlarm(secondOrder, owner, alarmIntent(secondOrder).getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertFalse(manager.ownsAlarm(alarmIntent(secondOrder)))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun `logout and account switch clear the active alarm`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        session.value = SessionState.LoggedOut
        runCurrent()
        assertFalse(manager.ownsAlarm(alarmIntent(firstOrder)))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        session.value = SessionState.LoggedIn(user)
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        session.value = SessionState.LoggedIn(user.copy(userId = 10))
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun `cold process waits for resolved session and preserves same owner alarm`() = runTest {
        val first = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        first.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        session.value = SessionState.Unknown
        val recreated = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        recreated.startSessionObservation()
        val waiting = async { recreated.awaitResolvedSession() }
        runCurrent()
        assertFalse(waiting.isCompleted)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        session.value = SessionState.LoggedIn(user)
        runCurrent()
        assertTrue(waiting.await())
        assertTrue(recreated.ownsAlarm(alarmIntent(firstOrder)))
    }

    @Test fun `unresolved session has a bounded wait`() = runTest {
        session.value = SessionState.Unknown
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val waiting = async { manager.awaitResolvedSession() }
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(waiting.await())
    }

    @Test fun `system alarm preview opens app rather than completion page`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        val showIntent = shadowOf(alarms.nextAlarmClock.showIntent).savedIntent
        assertEquals(MainActivity::class.java.name, showIntent.component?.className)
        assertEquals(Intent.ACTION_MAIN, showIntent.action)
        assertEquals(0, showIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test fun `same order reschedule and old plan cancellation cannot affect current reminder`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val firstTrigger = System.currentTimeMillis() + 60_000
        manager.scheduleCountdownAlarm(firstOrder, "护理", firstTrigger)
        val old = alarmIntent(firstOrder)
        manager.scheduleCountdownAlarm(firstOrder, "护理", firstTrigger + 30_000)
        manager.dismissAlarm(firstOrder, owner, firstTrigger)
        manager.cancelCountdownAlarmForOrder(firstOrder.copy(planId = firstOrder.planId + 1))
        assertFalse(manager.ownsAlarm(old))
        assertTrue(manager.ownsAlarm(alarmIntent(firstOrder)))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test fun `legacy alarm is adopted only from matching authorized ongoing order`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val legacy = legacyIntent()
        coEvery { recovery.read() } returns LocationRecoveryRecord(LocationAccount(1, 2, 3), firstOrder.orderId, "existing")
        coEvery { orders.getOrderState(firstOrder.orderId) } returns ApiResult.Success(
            ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS))
        manager.startSessionObservation()
        runCurrent()
        assertTrue(manager.acceptAlarm(legacy))
        assertTrue(manager.ownsAlarm(legacy))
    }

    @Test fun `legacy alarm with missing authorization or finished order is rejected`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val legacy = legacyIntent()
        assertFalse(manager.acceptAlarm(legacy))
        coVerify(exactly = 0) { orders.getOrderState(any()) }
        coEvery { recovery.read() } returns LocationRecoveryRecord(LocationAccount(1, 2, 3), firstOrder.orderId, "existing")
        coEvery { orders.getOrderState(firstOrder.orderId) } returns ApiResult.Success(
            ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED))
        assertFalse(manager.acceptAlarm(legacy))
        assertFalse(manager.ownsAlarm(legacy))
    }

    @Test fun `cancelled legacy validation and late old response cannot acquire ownership`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val legacy = legacyIntent()
        val response = CompletableDeferred<ApiResult<ServiceOrderStateModel>>()
        coEvery { recovery.read() } returns LocationRecoveryRecord(LocationAccount(1, 2, 3), firstOrder.orderId, "existing")
        coEvery { orders.getOrderState(firstOrder.orderId) } coAnswers { withContext(NonCancellable) { response.await() } }
        val waiting = async { manager.acceptAlarm(legacy) }
        runCurrent()
        waiting.cancel()
        manager.scheduleCountdownAlarm(secondOrder, "新订单", System.currentTimeMillis() + 60_000)
        response.complete(ApiResult.Success(ServiceOrderStateModel(firstOrder.orderId, 1)))
        waiting.join()
        assertFalse(manager.ownsAlarm(legacy))
        assertTrue(manager.ownsAlarm(alarmIntent(secondOrder)))
    }

    @Test fun `only confirmed state of active order cancels the reminder`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        orderState.value = ServiceOrderStateModel(secondOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        runCurrent()
        assertTrue(manager.ownsAlarm(alarmIntent(firstOrder)))
        orderState.value = null
        runCurrent()
        assertTrue(manager.ownsAlarm(alarmIntent(firstOrder)))
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
        runCurrent()
        assertTrue(manager.ownsAlarm(alarmIntent(firstOrder)))
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_CANCELLED)
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertFalse(manager.ownsAlarm(alarmIntent(firstOrder)))
    }

    @Test fun `confirmed terminal state rejects ownership before the cleanup observer runs`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        val intent = alarmIntent(firstOrder)

        for (terminal in listOf(ServiceOrderStateModel.STATE_COMPLETED, ServiceOrderStateModel.STATE_CANCELLED)) {
            orderState.value = ServiceOrderStateModel(firstOrder.orderId, terminal)
            // 不执行 runCurrent：排程仍在，不能等待异步清理后才拒绝过期提醒。
            assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
            assertFalse(manager.ownsAlarm(intent))
            assertFalse(manager.acceptAlarm(intent))
        }
        orderState.value = null
        assertTrue(manager.ownsAlarm(intent))
        orderState.value = ServiceOrderStateModel(secondOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        assertTrue(manager.ownsAlarm(intent))
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
        assertTrue(manager.ownsAlarm(intent))
    }

    @Test fun `old notification pending intents retain old immutable identity after replacement`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val trigger = System.currentTimeMillis() + 60_000
        manager.scheduleCountdownAlarm(firstOrder, "旧护理", trigger)
        val ui = CountdownNotificationUiDelegate(context, "countdown_test", 3002, 3003)
        val oldNotification = ui.buildCountdownCompletionNotification(firstOrder, "旧护理", owner, trigger)
        manager.scheduleCountdownAlarm(secondOrder, "新护理", trigger + 60_000)
        val newNotification = ui.buildCountdownCompletionNotification(secondOrder, "新护理", owner, trigger + 60_000)
        assertNotEquals(oldNotification.contentIntent, newNotification.contentIntent)
        val oldDismiss = shadowOf(oldNotification.actions.first().actionIntent).savedIntent
        assertEquals(firstOrder, CountdownNotificationManager.extractOrderKey(oldDismiss))
        assertEquals(trigger, oldDismiss.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        manager.dismissAlarm(CountdownNotificationManager.extractOrderKey(oldDismiss),
            oldDismiss.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER),
            oldDismiss.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertTrue(manager.ownsAlarm(alarmIntent(secondOrder)))
    }

    @Test fun `empty account transitions do not start services or broadcast cleanup`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        val app = shadowOf(context as Application)
        val broadcastsBefore = app.broadcastIntents.size
        session.value = SessionState.LoggedOut
        manager.startSessionObservation()
        runCurrent()
        session.value = SessionState.LoggedIn(user)
        runCurrent()
        session.value = SessionState.LoggedOut
        runCurrent()
        assertEquals(broadcastsBefore, app.broadcastIntents.size)
        assertNull(app.nextStoppedService)
    }

    @Test fun `terminal state before a late schedule never recreates ended order alarm`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "迟到的旧订单", System.currentTimeMillis() + 60_000)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        manager.scheduleCountdownAlarm(secondOrder, "新订单", System.currentTimeMillis() + 60_000)
        manager.scheduleCountdownAlarm(firstOrder, "迟到的旧订单", System.currentTimeMillis() + 60_000)
        assertTrue(manager.ownsAlarm(alarmIntent(secondOrder)))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test fun `new account schedule before delayed observer is not cancelled by previous account`() = runTest {
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "旧账号", System.currentTimeMillis() + 60_000)
        session.value = SessionState.LoggedIn(user.copy(userId = 10))
        // 账号已经发布，观察协程尚未获调度；此时业务可同步登记新账号订单。
        manager.scheduleCountdownAlarm(secondOrder, "新账号", System.currentTimeMillis() + 120_000)
        val next = alarmIntent(secondOrder).apply {
            putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, "1:2:10")
        }
        assertTrue(manager.ownsAlarm(next))
        runCurrent()
        assertTrue(manager.ownsAlarm(next))
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test fun `queued old terminal emission cannot cancel a newer running schedule`() = runTest {
        val emitted = MutableStateFlow<ServiceOrderStateModel?>(null)
        var latest: ServiceOrderStateModel? = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
        // 模拟收集者处理旧发射时，StateFlow.value 已变为新的执行状态。
        val states = object : StateFlow<ServiceOrderStateModel?> by emitted {
            override val value: ServiceOrderStateModel? get() = latest
        }
        every { lifecycle.orderState } returns states
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        val trigger = System.currentTimeMillis() + 60_000
        manager.scheduleCountdownAlarm(firstOrder, "原排程", trigger)
        emitted.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        manager.scheduleCountdownAlarm(firstOrder, "重新执行", trigger + 60_000)

        runCurrent()

        assertTrue(manager.ownsAlarm(alarmIntent(firstOrder)))
        assertEquals(trigger + 60_000, alarms.nextAlarmClock.triggerTime)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)

        // 当前值真的结束时仍应清理，即使触发重查的发射携带另一条旧终态。
        latest = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        emitted.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_CANCELLED)
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertFalse(manager.ownsAlarm(alarmIntent(firstOrder)))
    }

    @Test fun `queued resolved session emission does not clear a currently unknown session`() = runTest {
        val emitted = MutableStateFlow<SessionState>(SessionState.LoggedIn(user))
        var latest: SessionState = SessionState.LoggedIn(user)
        val sessions = object : StateFlow<SessionState> by emitted {
            override val value: SessionState get() = latest
        }
        every { repository.sessionState } returns sessions
        val manager = CountdownNotificationManager(context, notifications, alarms, repository, recovery, orders, lifecycle, backgroundScope, timeProvider)
        manager.startSessionObservation()
        runCurrent()
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000)
        latest = SessionState.Unknown
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)

        runCurrent()

        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        latest = SessionState.LoggedOut
        emitted.value = SessionState.LoggedOut
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    @Config(sdk = [28, 31, 34, 36])
    fun `future pending alarm restores original full identity and deadline after platform loss`() = runTest {
        val manager = newManager(backgroundScope)
        val trigger = System.currentTimeMillis() + 60_000L
        manager.scheduleCountdownAlarm(firstOrder, "原护理", trigger)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        val recreated = newManager(backgroundScope)

        assertTrue(recreated.restorePendingCountdownAlarm({ true }))

        val restored = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        assertEquals(firstOrder, CountdownNotificationManager.extractOrderKey(restored))
        assertEquals(owner, restored.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER))
        assertEquals(trigger, restored.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
        assertEquals("原护理", CountdownNotificationManager.extractServiceName(restored, ""))
    }

    @Test
    @Config(sdk = [31, 34, 36])
    fun `permission grant restoration rechecks exact capability and preserves pending metadata`() = runTest {
        val manager = newManager(backgroundScope)
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000L)
        val original = alarmIntent(firstOrder)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(manager.restorePendingCountdownAlarm({ true }, requireExactAlarm = true))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertTrue(manager.ownsAlarm(original))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(manager.restorePendingCountdownAlarm({ true }, requireExactAlarm = true))
        assertEquals(original.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L), alarms.nextAlarmClock?.triggerTime)
    }

    @Test fun `restoration never resurrects cancelled expired or foreign account alarms`() = runTest {
        val manager = newManager(backgroundScope)
        val trigger = System.currentTimeMillis() + 60_000L
        manager.scheduleCountdownAlarm(firstOrder, "护理", trigger)
        manager.cancelCountdownAlarmForOrder(firstOrder)
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() - 1L)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        manager.scheduleCountdownAlarm(firstOrder, "护理", trigger)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        session.value = SessionState.LoggedIn(user.copy(userId = 99))
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        session.value = SessionState.LoggedOut
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        session.value = SessionState.Unknown
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun `restoration rechecks privacy terminal state and the latest replacement record`() = runTest {
        val manager = newManager(backgroundScope)
        val trigger = System.currentTimeMillis() + 60_000L
        manager.scheduleCountdownAlarm(firstOrder, "旧护理", trigger)
        manager.scheduleCountdownAlarm(secondOrder, "新护理", trigger + 30_000L)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        assertFalse(manager.restorePendingCountdownAlarm({ false }))
        orderState.value = ServiceOrderStateModel(secondOrder.orderId, ServiceOrderStateModel.STATE_COMPLETED)
        assertFalse(manager.restorePendingCountdownAlarm({ true }))
        orderState.value = null
        assertTrue(manager.restorePendingCountdownAlarm({ true }))
        val restored = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
        assertEquals(secondOrder, CountdownNotificationManager.extractOrderKey(restored))
        assertEquals(trigger + 30_000L, restored.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
    }

    @Test fun `initial resolved session retries restoration once after a slow cold start`() = runTest {
        val manager = newManager(backgroundScope)
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000L)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        session.value = SessionState.Unknown
        manager.startSessionObservation { true }
        runCurrent()
        advanceTimeBy(6_000L)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        session.value = SessionState.LoggedIn(user)
        runCurrent()
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        orderState.value = ServiceOrderStateModel(firstOrder.orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun `cancel while session restoration is pending cannot recreate an alarm`() = runTest {
        val manager = newManager(backgroundScope)
        manager.scheduleCountdownAlarm(firstOrder, "护理", System.currentTimeMillis() + 60_000L)
        session.value = SessionState.Unknown
        manager.startSessionObservation { true }
        runCurrent()
        manager.cancelCountdownAlarmForOrder(firstOrder)
        session.value = SessionState.LoggedIn(user)
        runCurrent()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun `clock corrections and process recreation preserve identity while remapping RTC time`() = runTest {
        clock = ServiceTimeSnapshot(1_000_000L, 100_000L, 7)
        val deadline = ServiceCountdownDeadline(1_060_000L, 160_000L, 7)
        newManager(backgroundScope).scheduleCountdownAlarm(firstOrder, "原护理", deadline.wallTimeMillis, deadline)
        val original = Intent(shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent)

        for (shift in listOf(300_000L, -300_000L)) {
            clock = ServiceTimeSnapshot(1_010_000L + shift, 110_000L, 7)
            alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
            val recreated = newManager(backgroundScope)

            assertTrue(recreated.restorePendingCountdownAlarm({ true }, allowDueMonotonicAlarm = true))

            val restored = shadowOf(shadowOf(alarms).scheduledAlarms.single().operation).savedIntent
            assertEquals(1_060_000L + shift, alarms.nextAlarmClock.triggerTime)
            assertEquals(firstOrder, CountdownNotificationManager.extractOrderKey(restored))
            assertEquals(owner, restored.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER))
            assertEquals(deadline.wallTimeMillis, restored.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L))
            assertEquals("原护理", CountdownNotificationManager.extractServiceName(restored, ""))
            assertTrue(recreated.ownsAlarm(original))
            assertTrue(recreated.ownsAlarm(restored))
            assertEquals(50_000L, deadline.remainingMillis(requireNotNull(clock)))
        }
    }

    @Test fun `reboot restores by wall fallback without reusing the previous boot elapsed deadline`() = runTest {
        clock = ServiceTimeSnapshot(1_000_000L, 100_000L, 7)
        val deadline = ServiceCountdownDeadline(1_060_000L, 160_000L, 7)
        newManager(backgroundScope).scheduleCountdownAlarm(firstOrder, "护理", deadline.wallTimeMillis, deadline)
        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        clock = ServiceTimeSnapshot(1_020_000L, 5_000L, 8)
        val recreated = newManager(backgroundScope)

        assertTrue(recreated.restorePendingCountdownAlarm({ true }))
        assertEquals(deadline.wallTimeMillis, alarms.nextAlarmClock.triggerTime)
        assertEquals(40_000L, deadline.remainingMillis(requireNotNull(clock)))

        alarms.cancel(requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation))
        clock = ServiceTimeSnapshot(1_060_001L, 6_000L, 8)
        assertFalse(recreated.restorePendingCountdownAlarm({ true }, allowDueMonotonicAlarm = true))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    private fun newManager(scope: kotlinx.coroutines.CoroutineScope) = CountdownNotificationManager(
        context, notifications, alarms, repository, recovery, orders, lifecycle, scope, timeProvider,
    )

    private fun legacyIntent(): Intent {
        context.getSharedPreferences("countdown_notification_prefs", Context.MODE_PRIVATE).edit()
            .putLong("key_last_scheduled_order_id", firstOrder.orderId).commit()
        return Intent().apply { putExtra("extra_request", firstOrder); putExtra("service_name", "旧护理") }
    }

    private fun alarmIntent(orderKey: OrderKey) = Intent(context, CountdownAlarmReceiver::class.java).apply {
        putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
        putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, owner)
        putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME,
            context.getSharedPreferences("countdown_notification_prefs", Context.MODE_PRIVATE).getLong("trigger_time", 0L))
    }
}
