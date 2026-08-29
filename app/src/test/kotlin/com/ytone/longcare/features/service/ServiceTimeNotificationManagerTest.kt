package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.UserScopeKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ServiceTimeNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var workManager: WorkManager
    private lateinit var reminderRepository: UserServiceReminderRepository
    private lateinit var storageRegistry: UserStorageRegistry
    private lateinit var manager: ServiceTimeNotificationManager
    private val codec = ServiceTimeTaskCodec()
    private val lease = UserStorageLease(
        scopeKey = UserScopeKey(1, 2, 3),
        sessionEpoch = SessionEpoch(10),
        generation = StorageGeneration(20),
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        reminderRepository = mockk(relaxed = true)
        storageRegistry = mockk()
        every { storageRegistry.requireCurrentLease() } returns lease
        coEvery { reminderRepository.wasProcessedSince(any(), any()) } returns false

        manager = ServiceTimeNotificationManager(
            context = context,
            notificationManager = notificationManager,
            alarmManager = mockk<AlarmManager>(relaxed = true),
            workManager = workManager,
            reminderRepository = reminderRepository,
            storageRegistry = storageRegistry,
            taskCodec = codec,
        )
    }

    @Test
    fun `future reminder persists scoped identity and schedules scoped work`() = runTest {
        val orderId = 12_345L
        val endTime = System.currentTimeMillis() + 60_000L
        val identity = codec.currentExecution(lease, orderId).taskIdentity

        manager.scheduleServiceTimeEndNotification(orderId, "测试服务", endTime)

        coVerify(exactly = 1) {
            reminderRepository.upsert(
                match<PendingServiceReminder> {
                    it.taskIdentity == identity && it.orderId == orderId && it.triggerAtMillis == endTime
                }
            )
        }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                codec.workUniqueName(identity),
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
        verify(exactly = 0) { notificationManager.notify(any(), any<Notification>()) }
    }

    @Test
    fun `past reminder notifies and records only current session`() = runTest {
        val orderId = 23_456L
        val identity = codec.currentExecution(lease, orderId).taskIdentity

        manager.scheduleServiceTimeEndNotification(
            orderId,
            "过期服务",
            System.currentTimeMillis() - 1_000L,
        )

        verify(exactly = 1) { notificationManager.notify(codec.notificationId(identity), any()) }
        coVerify(exactly = 1) { reminderRepository.markProcessed(identity, any()) }
        coVerify(exactly = 1) { reminderRepository.delete(identity) }
        coVerify(exactly = 0) { reminderRepository.upsert(any()) }
    }

    @Test
    fun `stale callback is silent before notification or room mutation`() = runTest {
        val staleLease = lease.copy(generation = StorageGeneration(19))
        val payload = ServiceTimeTaskPayload(
            execution = codec.currentExecution(staleLease, 34_567L),
            serviceName = "旧任务",
            triggerAtMillis = 1,
        )

        val shown = manager.handleTriggered(payload)

        assertFalse(shown)
        verify(exactly = 0) { notificationManager.notify(any(), any<Notification>()) }
        coVerify(exactly = 0) { reminderRepository.markProcessed(any(), any()) }
        coVerify(exactly = 0) { reminderRepository.delete(any()) }
    }

    @Test
    fun `deduplication and cancellation use full task identity`() = runTest {
        val orderId = 45_678L
        val identity = codec.currentExecution(lease, orderId).taskIdentity
        coEvery { reminderRepository.wasProcessedSince(identity, any()) } returnsMany listOf(false, true)

        manager.showServiceTimeEndNotification(orderId, "服务")
        manager.showServiceTimeEndNotification(orderId, "服务")
        verify(exactly = 1) { notificationManager.notify(codec.notificationId(identity), any()) }

        manager.cancelServiceTimeEndNotification(orderId)

        coVerify(exactly = 1) { reminderRepository.clearProcessed(identity) }
        verify(exactly = 1) { workManager.cancelUniqueWork(codec.workUniqueName(identity)) }
        verify(exactly = 1) { notificationManager.cancel(codec.notificationId(identity)) }
    }
}
