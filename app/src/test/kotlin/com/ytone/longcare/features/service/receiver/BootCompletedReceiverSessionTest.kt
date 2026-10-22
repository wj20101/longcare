package com.ytone.longcare.features.service.receiver

import android.app.AlarmManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.storage.PendingOrdersStorage
import com.ytone.longcare.model.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BootCompletedReceiverSessionTest {

    @Test
    fun `cold boot waits until persisted session is resolved`() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Unknown)
        val repository = mockk<UserSessionRepository>()
        every { repository.sessionState } returns state

        val resolved = async { repository.awaitResolvedSessionState() }
        runCurrent()
        assertFalse(resolved.isCompleted)

        val loggedIn = SessionState.LoggedIn(User(userId = 7, token = "token"))
        state.value = loggedIn

        assertEquals(loggedIn, resolved.await())
    }

    @Test
    @Config(sdk = [28, 34])
    fun `boot restores countdown before the unrelated pending order list early return`() = runTest {
        val (receiver, countdownManager, serviceNotifications, pendingOrders, _, _, pendingResult) =
            receiverFixture(backgroundScope)

        receiver.onReceive(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_BOOT_COMPLETED))
        runCurrent()

        verifyOrder {
            countdownManager.restorePendingCountdownAlarm(any(), false)
            pendingOrders.getAllPendingOrders()
        }
        verify(exactly = 0) { serviceNotifications.scheduleServiceTimeEndNotification(any(), any(), any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    @Config(sdk = [31, 34, 36])
    fun `permission grant routes only to countdown with a fresh exact capability check`() = runTest {
        val (receiver, countdownManager, _, pendingOrders, _, _, pendingResult) =
            receiverFixture(backgroundScope)

        receiver.onReceive(ApplicationProvider.getApplicationContext(),
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        runCurrent()

        verify(exactly = 1) { countdownManager.restorePendingCountdownAlarm(any(), true) }
        verify(exactly = 0) { pendingOrders.getAllPendingOrders() }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test fun `privacy denial unknown timeout and unrelated actions never restore`() = runTest {
        val (receiver, countdownManager, _, pendingOrders, privacyConsent, sessions, pendingResult) =
            receiverFixture(backgroundScope)
        val context = ApplicationProvider.getApplicationContext<Context>()
        every { privacyConsent.isPrivacyConsented } returns false
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        runCurrent()
        every { privacyConsent.isPrivacyConsented } returns true
        receiver.onReceive(context, Intent("untrusted.action"))
        verify(exactly = 0) { receiver.goAsync() }
        val unknown = MutableStateFlow<SessionState>(SessionState.Unknown)
        every { sessions.sessionState } returns unknown
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceTimeBy(5_001L)
        runCurrent()

        verify(exactly = 0) { countdownManager.restorePendingCountdownAlarm(any(), any()) }
        verify(exactly = 0) { pendingOrders.getAllPendingOrders() }
        verify(exactly = 1) { receiver.goAsync() }
        verify(exactly = 1) { sessions.sessionState }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    @Config(sdk = [28, 30])
    fun `exact permission action is not a restore entry point on older platforms`() = runTest {
        val (receiver, countdownManager, _, pendingOrders) = receiverFixture(backgroundScope)

        receiver.onReceive(ApplicationProvider.getApplicationContext(),
            Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
        runCurrent()

        verify(exactly = 0) { receiver.goAsync() }
        verify(exactly = 0) { countdownManager.restorePendingCountdownAlarm(any(), any()) }
        verify(exactly = 0) { pendingOrders.getAllPendingOrders() }
    }

    private data class ReceiverFixture(
        val receiver: BootCompletedReceiver,
        val countdownManager: CountdownNotificationManager,
        val serviceNotifications: ServiceTimeNotificationManager,
        val pendingOrders: PendingOrdersStorage,
        val privacyConsent: PrivacyConsentManager,
        val sessions: UserSessionRepository,
        val pendingResult: BroadcastReceiver.PendingResult,
    )

    private fun receiverFixture(scope: CoroutineScope): ReceiverFixture {
        val receiver = spyk(BootCompletedReceiver())
        ReflectionHelpers.setField(receiver, "injected", true)
        val countdownManager = mockk<CountdownNotificationManager>(relaxed = true)
        val serviceNotifications = mockk<ServiceTimeNotificationManager>(relaxed = true)
        val pendingOrders = mockk<PendingOrdersStorage>()
        every { pendingOrders.getAllPendingOrders() } returns emptyList()
        val privacyConsent = mockk<PrivacyConsentManager>()
        every { privacyConsent.isPrivacyConsented } returns true
        val state = MutableStateFlow<SessionState>(SessionState.LoggedIn(User(userId = 7)))
        val sessions = mockk<UserSessionRepository>()
        every { sessions.sessionState } returns state
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        // Keep injected collaborators outside spy-property DSL chains: MockK otherwise records
        // autogenerated child mocks instead of the objects actually used by onReceive.
        receiver.countdownNotificationManager = countdownManager
        receiver.serviceTimeNotificationManager = serviceNotifications
        receiver.pendingOrdersStorage = pendingOrders
        receiver.privacyConsentManager = privacyConsent
        receiver.userSessionRepository = sessions
        receiver.applicationScope = scope
        every { receiver.goAsync() } returns pendingResult
        return ReceiverFixture(receiver, countdownManager, serviceNotifications, pendingOrders,
            privacyConsent, sessions, pendingResult)
    }
}
