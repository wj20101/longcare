package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserCountdownTaskRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CountdownNotificationManagerUserScopeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val storageRegistry = mockk<UserStorageRegistry>(relaxed = true)
    private val repository = mockk<UserCountdownTaskRepository>()
    private val codec = CountdownTaskCodec()
    private val gate = mockk<CountdownTaskExecutionGate>()
    private val applicationScope = TestScope()
    private lateinit var manager: CountdownNotificationManager

    @Before
    fun setUp() {
        every { alarmManager.canScheduleExactAlarms() } returns false
        manager = CountdownNotificationManager(
            context = context,
            notificationManager = notificationManager,
            alarmManager = alarmManager,
            storageRegistry = storageRegistry,
            taskRepository = repository,
            taskCodec = codec,
            executionGate = gate,
            applicationScope = applicationScope,
        )
    }

    @Test
    fun `same order for A and B creates distinct pending intent and notification identities`() = runTest {
        val orderKey = OrderKey(77, 8)
        val payloadA = payload(UserScopeKey(1, 2, 3), epoch = 10, generation = 1, orderKey)
        val payloadB = payload(UserScopeKey(1, 2, 4), epoch = 20, generation = 2, orderKey)
        every { gate.isCurrent(any()) } returns true
        coEvery { repository.getAllForCurrentSession() } returns emptyList()
        coEvery { repository.upsert(any()) } returns Unit
        val pendingIntents = mutableListOf<PendingIntent>()
        every {
            alarmManager.setAndAllowWhileIdle(any(), any(), capture(pendingIntents))
        } returns Unit

        assertTrue(manager.scheduleNow(payloadA))
        assertTrue(manager.scheduleNow(payloadB))

        assertNotEquals(pendingIntents[0], pendingIntents[1])
        assertNotEquals(
            codec.completionNotificationId(payloadA.execution.taskIdentity),
            codec.completionNotificationId(payloadB.execution.taskIdentity),
        )
        assertNotEquals(
            codec.requestCode(payloadA.execution.taskIdentity, CountdownIntentPurpose.DISMISS),
            codec.requestCode(payloadB.execution.taskIdentity, CountdownIntentPurpose.DISMISS),
        )
        coVerify(exactly = 2) { repository.upsert(any()) }
    }

    @Test
    fun `stale generation stops before Room Alarm or notification side effects`() = runTest {
        val payload = payload(UserScopeKey(1, 2, 3), 10, 1, OrderKey(77, 8))
        every { gate.isCurrent(payload) } returns false

        assertFalse(manager.scheduleNow(payload))

        coVerify(exactly = 0) { repository.getAllForCurrentSession() }
        coVerify(exactly = 0) { repository.upsert(any()) }
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any()) }
    }

    @Test
    fun `cleanup cancels only the revoked identity notification IDs and clears its Room rows`() = runTest {
        val scope = UserScopeKey(1, 2, 3)
        val payload = payload(scope, 10, 1, OrderKey(77, 8))
        val identity = SessionRuntimeIdentity(scope, SessionEpoch(10))
        coEvery { repository.snapshotForCleanup(identity) } returns listOf(payload.toPendingTask())
        coEvery { repository.clearForCleanup(identity) } returns Unit

        manager.cleanup(identity)

        verify(exactly = 1) {
            notificationManager.cancel(codec.completionNotificationId(payload.execution.taskIdentity))
        }
        verify(exactly = 1) {
            notificationManager.cancel(codec.foregroundNotificationId(payload.execution.taskIdentity))
        }
        coVerify(exactly = 1) { repository.clearForCleanup(identity) }
    }

    private fun payload(
        scopeKey: UserScopeKey,
        epoch: Long,
        generation: Long,
        orderKey: OrderKey,
    ) = codec.currentPayload(
        lease = UserStorageLease(
            scopeKey = scopeKey,
            sessionEpoch = SessionEpoch(epoch),
            generation = StorageGeneration(generation),
        ),
        orderKey = orderKey,
        serviceName = "护理",
        triggerAtMillis = 5_000,
    )
}
