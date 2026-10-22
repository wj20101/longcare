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
        val receiver = receiver(backgroundScope)

        receiver.onReceive(ApplicationProvider.getApplicationContext(), Intent(Intent.ACTION_BOOT_COMPLETED))
        runCurrent()

        verifyOrder {
            receiver.countdownNotificationManager.restorePendingCountdownAlarm(any(), false)
            receiver.pendingOrdersStorage.getAllPendingOrders()
        }
        verify(exactly = 0) { receiver.serviceTimeNotificationManager.scheduleServiceTimeEndNotification(any(), any(), any()) }
    }

    @Test
    @Config(sdk = [31, 34, 36])
    fun `permission grant routes only to countdown with a fresh exact capability check`() = runTest {
        val receiver = receiver(backgroundScope)

        receiver.onReceive(ApplicationProvider.getApplicationContext(),
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        runCurrent()

        verify(exactly = 1) { receiver.countdownNotificationManager.restorePendingCountdownAlarm(any(), true) }
        verify(exactly = 0) { receiver.pendingOrdersStorage.getAllPendingOrders() }
    }

    @Test fun `privacy denial unknown timeout and unrelated actions never restore`() = runTest {
        val receiver = receiver(backgroundScope)
        val context = ApplicationProvider.getApplicationContext<Context>()
        every { receiver.privacyConsentManager.isPrivacyConsented } returns false
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        runCurrent()
        every { receiver.privacyConsentManager.isPrivacyConsented } returns true
        receiver.onReceive(context, Intent("untrusted.action"))
        val unknown = MutableStateFlow<SessionState>(SessionState.Unknown)
        every { receiver.userSessionRepository.sessionState } returns unknown
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceTimeBy(5_001L)
        runCurrent()

        verify(exactly = 0) { receiver.countdownNotificationManager.restorePendingCountdownAlarm(any(), any()) }
        verify(exactly = 0) { receiver.pendingOrdersStorage.getAllPendingOrders() }
    }

    private fun receiver(scope: CoroutineScope): BootCompletedReceiver {
        val receiver = spyk(BootCompletedReceiver())
        ReflectionHelpers.setField(receiver, "injected", true)
        receiver.countdownNotificationManager = mockk<CountdownNotificationManager>(relaxed = true)
        receiver.serviceTimeNotificationManager = mockk(relaxed = true)
        receiver.pendingOrdersStorage = mockk { every { getAllPendingOrders() } returns emptyList() }
        receiver.privacyConsentManager = mockk<PrivacyConsentManager> { every { isPrivacyConsented } returns true }
        val state = MutableStateFlow<SessionState>(SessionState.LoggedIn(User(userId = 7)))
        receiver.userSessionRepository = mockk { every { sessionState } returns state }
        receiver.applicationScope = scope
        every { receiver.goAsync() } returns mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        return receiver
    }
}
