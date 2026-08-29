package com.ytone.longcare.features.service.receiver

import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserScopeKey
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BootCompletedReceiverSessionTest {

    @Test
    fun `cold boot waits until persisted session is resolved`() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Unknown)
        val repository = mockk<UserSessionRepository>()
        every { repository.sessionState } returns state

        val resolved = async { repository.awaitResolvedSessionState() }
        runCurrent()
        assertFalse(resolved.isCompleted)

        val loggedIn = SessionState.LoggedIn(
            CurrentUser(
                scopeKey = UserScopeKey(companyId = 1, accountId = 2, userId = 7),
                userName = "user-7",
                headUrl = "",
                userIdentity = 1,
                gender = 0,
            ),
        )
        state.value = loggedIn

        assertEquals(loggedIn, resolved.await())
    }

    @Test
    fun `pending or corrupted session never opens reminder repository`() = runTest {
        val reminders = mockk<UserServiceReminderRepository>()
        val manager = mockk<ServiceTimeNotificationManager>()

        assertEquals(
            0,
            recoverCurrentSessionNotifications(SessionState.Unknown, reminders, manager, nowMillis = 100),
        )
        assertEquals(
            0,
            recoverCurrentSessionNotifications(SessionState.LoggedOut, reminders, manager, nowMillis = 100),
        )

        coVerify(exactly = 0) { reminders.getAllForCurrentSession() }
        coVerify(exactly = 0) { manager.reschedule(any()) }
    }

    @Test
    fun `boot restores only current B reminders and removes expired rows`() = runTest {
        val scopeA = UserScopeKey(1, 2, 7)
        val scopeB = UserScopeKey(1, 3, 8)
        val reminderA = reminder(scopeA, epoch = 10, orderId = 99, trigger = 200)
        val reminderB = reminder(scopeB, epoch = 20, orderId = 99, trigger = 200)
        val expiredB = reminder(scopeB, epoch = 20, orderId = 100, trigger = 50)
        val reminders = mockk<UserServiceReminderRepository>()
        val manager = mockk<ServiceTimeNotificationManager>()
        coEvery { reminders.getAllForCurrentSession() } returns listOf(reminderA, reminderB, expiredB)
        coEvery { manager.reschedule(reminderA) } throws IllegalArgumentException("A is not current")
        coEvery { manager.reschedule(reminderB) } returns Unit
        coEvery { reminders.delete(expiredB.taskIdentity) } returns Unit
        val sessionB = SessionState.LoggedIn(
            CurrentUser(scopeB, "B", "", userIdentity = 1, gender = 0)
        )

        val recovered = recoverCurrentSessionNotifications(
            sessionState = sessionB,
            reminderRepository = reminders,
            notificationManager = manager,
            nowMillis = 100,
        )

        assertEquals(1, recovered)
        coVerify(exactly = 1) { manager.reschedule(reminderB) }
        coVerify(exactly = 1) { reminders.delete(expiredB.taskIdentity) }
        assertTrue(reminderA.taskIdentity != reminderB.taskIdentity)
    }

    private fun reminder(
        scope: UserScopeKey,
        epoch: Long,
        orderId: Long,
        trigger: Long,
    ) = PendingServiceReminder(
        taskIdentity = UserTaskIdentity(
            namespaceId = scope.namespaceId(),
            sessionEpoch = SessionEpoch(epoch),
            taskType = SERVICE_TIME_END_TASK_TYPE,
            businessId = orderId.toString(),
        ),
        orderId = orderId,
        serviceName = "service-$orderId",
        triggerAtMillis = trigger,
    )
}
