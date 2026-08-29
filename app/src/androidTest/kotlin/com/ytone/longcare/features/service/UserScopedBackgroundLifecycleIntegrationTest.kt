package com.ytone.longcare.features.service

import android.app.AlarmManager
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.squareup.moshi.Moshi
import com.ytone.longcare.data.userstorage.UserDataStoreRegistry
import com.ytone.longcare.data.userstorage.UserDatabaseFactory
import com.ytone.longcare.data.userstorage.UserNamespaceMetadataStore
import com.ytone.longcare.data.userstorage.UserNamespacePathsFactory
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.userstorage.PendingCountdownTask
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.ServiceReminderCleanupSnapshot
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserCountdownTaskRepository
import com.ytone.longcare.domain.userstorage.UserServiceReminderRepository
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.features.countdown.manager.CountdownIntentPurpose
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.manager.CountdownTaskCodec
import com.ytone.longcare.features.countdown.manager.CountdownTaskExecutionGate
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiver
import com.ytone.longcare.features.countdown.receiver.CountdownAlarmReceiverDelegate
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserScopedBackgroundLifecycleIntegrationTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var workManager: WorkManager
    private lateinit var pathsFactory: UserNamespacePathsFactory
    private lateinit var registry: UserStorageRegistry
    private lateinit var applicationScope: CoroutineScope
    private lateinit var serviceRepository: InMemoryServiceReminderRepository
    private lateinit var countdownRepository: InMemoryCountdownTaskRepository
    private lateinit var serviceCodec: ServiceTimeTaskCodec
    private lateinit var countdownCodec: CountdownTaskCodec
    private lateinit var serviceManager: ServiceTimeNotificationManager
    private lateinit var countdownManager: CountdownNotificationManager
    private val scopeA = UserScopeKey(70_001, 70_002, 70_003)
    private val scopeB = UserScopeKey(80_001, 80_002, 80_003)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        workManager = WorkManager.getInstance(context)
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        pathsFactory = UserNamespacePathsFactory(context)
        registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        serviceRepository = InMemoryServiceReminderRepository()
        countdownRepository = InMemoryCountdownTaskRepository()
        serviceCodec = ServiceTimeTaskCodec()
        countdownCodec = CountdownTaskCodec()
        serviceManager = ServiceTimeNotificationManager(
            context,
            notificationManager,
            alarmManager,
            workManager,
            serviceRepository,
            registry,
            serviceCodec,
        )
        val countdownGate = CountdownTaskExecutionGate(registry, countdownCodec)
        countdownManager = CountdownNotificationManager(
            context,
            notificationManager,
            alarmManager,
            registry,
            countdownRepository,
            countdownCodec,
            countdownGate,
            applicationScope,
        )
    }

    @After
    fun tearDown() = runBlocking {
        runCatching { registry.close() }
        workManager.cancelAllWorkByTag(TEST_WORK_TAG).result.get(10, TimeUnit.SECONDS)
        listOf(scopeA, scopeB).forEach { scope ->
            val paths = pathsFactory.forScope(scope)
            context.deleteDatabase(paths.databaseFile.name)
            paths.dataStoreFile.delete()
            paths.namespaceRoot.deleteRecursively()
            paths.sessionRoot.parentFile?.deleteRecursively()
        }
        applicationScope.cancel()
    }

    @Test
    fun aScheduleLogoutBLoginRecreationAndOldTriggersRemainIsolated() = runBlocking {
        val epochA = SessionEpoch(101)
        val leaseA = registry.open(scopeA, epochA)
        val countdownA = countdownCodec.currentPayload(
            leaseA,
            OrderKey(orderId = 9001, planId = 91),
            "A-countdown",
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
        )
        val serviceA = ServiceTimeTaskPayload(
            execution = serviceCodec.currentExecution(leaseA, orderId = 9001),
            serviceName = "A-service",
            triggerAtMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
        )

        assertTrue(countdownManager.scheduleNow(countdownA))
        serviceManager.scheduleServiceTimeEndNotification(
            serviceA.orderId,
            serviceA.serviceName,
            serviceA.triggerAtMillis,
        )
        assertTrue(hasCountdownAlarm(countdownA.execution.taskIdentity))
        assertTrue(hasServiceAlarm(serviceA.execution.taskIdentity))

        val runtimeA = SessionRuntimeIdentity(scopeA, epochA)
        serviceManager.cleanup(runtimeA)
        countdownManager.cleanup(runtimeA)
        registry.close()

        val epochB = SessionEpoch(202)
        val leaseB = registry.open(scopeB, epochB)
        val countdownB = countdownCodec.currentPayload(
            leaseB,
            OrderKey(orderId = 9001, planId = 91),
            "B-countdown",
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
        )
        countdownRepository.upsert(countdownB.toPendingTask())
        val serviceBIdentity = serviceCodec.currentExecution(leaseB, orderId = 9001).taskIdentity
        serviceRepository.upsert(
            PendingServiceReminder(
                taskIdentity = serviceBIdentity,
                orderId = 9001,
                serviceName = "B-service",
                triggerAtMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
            ),
        )

        // Reopen the same ACTIVE B namespace to model process recreation after a device restart.
        registry.close()
        registry.open(scopeB, epochB)

        val countdownGateAfterRestart = CountdownTaskExecutionGate(registry, countdownCodec)
        CountdownAlarmReceiverDelegate.handle(
            context,
            countdownA,
            countdownCodec,
            countdownGateAfterRestart,
        )
        assertFalse(serviceManager.handleTriggered(serviceA))

        assertTrue(countdownRepository.tasks.any { it.taskIdentity == countdownB.execution.taskIdentity })
        assertTrue(serviceRepository.reminders.any { it.taskIdentity == serviceBIdentity })
        assertNull(findCountdownAlarm(countdownA.execution.taskIdentity))
        assertNull(findServiceAlarm(serviceA.execution.taskIdentity))
        val aWork = workManager.getWorkInfosByTag(
            serviceCodec.epochWorkTag(scopeA.namespaceId(), epochA),
        ).get(10, TimeUnit.SECONDS)
        assertTrue(aWork.none { it.state in ACTIVE_WORK_STATES })

        val activeNotificationIds = notificationManager.activeNotifications.map { it.id }.toSet()
        assertFalse(serviceCodec.notificationId(serviceA.execution.taskIdentity) in activeNotificationIds)
        assertFalse(countdownCodec.completionNotificationId(countdownA.execution.taskIdentity) in activeNotificationIds)
        assertFalse(countdownCodec.foregroundNotificationId(countdownA.execution.taskIdentity) in activeNotificationIds)
        assertFalse(isServiceRunning(AlarmRingtoneService::class.java))
        assertFalse(isServiceRunning(CountdownForegroundService::class.java))
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any { running ->
            running.service.className == serviceClass.name
        }
    }

    private fun hasCountdownAlarm(identity: UserTaskIdentity): Boolean =
        findCountdownAlarm(identity) != null

    private fun findCountdownAlarm(identity: UserTaskIdentity): PendingIntent? =
        PendingIntentCompat.getBroadcast(
            context,
            countdownCodec.requestCode(identity, CountdownIntentPurpose.ALARM),
            Intent(context, CountdownAlarmReceiver::class.java).apply {
                data = countdownCodec.dataUri(identity, CountdownIntentPurpose.ALARM)
            },
            PendingIntent.FLAG_NO_CREATE,
            false,
        )

    private fun hasServiceAlarm(identity: UserTaskIdentity): Boolean =
        findServiceAlarm(identity) != null

    private fun findServiceAlarm(identity: UserTaskIdentity): PendingIntent? =
        PendingIntentCompat.getBroadcast(
            context,
            serviceCodec.alarmRequestCode(identity),
            Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
                data = serviceCodec.alarmDataUri(identity)
            },
            PendingIntent.FLAG_NO_CREATE,
            false,
        )

    private class InMemoryCountdownTaskRepository : UserCountdownTaskRepository {
        val tasks = mutableListOf<PendingCountdownTask>()

        override suspend fun upsert(task: PendingCountdownTask) {
            tasks.removeAll { it.taskIdentity == task.taskIdentity }
            tasks += task
        }

        override suspend fun getAllForCurrentSession(): List<PendingCountdownTask> = tasks.toList()

        override suspend fun delete(taskIdentity: UserTaskIdentity) {
            tasks.removeAll { it.taskIdentity == taskIdentity }
        }

        override suspend fun snapshotForCleanup(
            identity: SessionRuntimeIdentity,
        ): List<PendingCountdownTask> = tasks.filter { task ->
            task.taskIdentity.namespaceId == identity.scopeKey.namespaceId() &&
                task.taskIdentity.sessionEpoch == identity.sessionEpoch
        }

        override suspend fun clearForCleanup(identity: SessionRuntimeIdentity) {
            tasks.removeAll { task ->
                task.taskIdentity.namespaceId == identity.scopeKey.namespaceId() &&
                    task.taskIdentity.sessionEpoch == identity.sessionEpoch
            }
        }
    }

    private class InMemoryServiceReminderRepository : UserServiceReminderRepository {
        val reminders = mutableListOf<PendingServiceReminder>()
        private val processed = mutableMapOf<UserTaskIdentity, Long>()

        override suspend fun upsert(reminder: PendingServiceReminder) {
            reminders.removeAll { it.taskIdentity == reminder.taskIdentity }
            reminders += reminder
        }

        override suspend fun getAllForCurrentSession(): List<PendingServiceReminder> =
            reminders.toList()

        override suspend fun delete(taskIdentity: UserTaskIdentity) {
            reminders.removeAll { it.taskIdentity == taskIdentity }
        }

        override suspend fun deleteExpiredForCurrentSession(nowMillis: Long) {
            reminders.removeAll { it.triggerAtMillis <= nowMillis }
        }

        override suspend fun wasProcessedSince(
            taskIdentity: UserTaskIdentity,
            sinceMillis: Long,
        ): Boolean = (processed[taskIdentity] ?: Long.MIN_VALUE) >= sinceMillis

        override suspend fun markProcessed(taskIdentity: UserTaskIdentity, processedAtMillis: Long) {
            processed[taskIdentity] = processedAtMillis
        }

        override suspend fun clearProcessed(taskIdentity: UserTaskIdentity) {
            processed.remove(taskIdentity)
        }

        override suspend fun snapshotForCleanup(
            identity: SessionRuntimeIdentity,
        ): ServiceReminderCleanupSnapshot = ServiceReminderCleanupSnapshot(
            pendingReminders = reminders.filter { reminder ->
                reminder.taskIdentity.namespaceId == identity.scopeKey.namespaceId() &&
                    reminder.taskIdentity.sessionEpoch == identity.sessionEpoch
            },
            processedOrderIds = processed.keys.filter { task ->
                task.namespaceId == identity.scopeKey.namespaceId() &&
                    task.sessionEpoch == identity.sessionEpoch
            }.mapNotNullTo(linkedSetOf()) { it.businessId.toLongOrNull() },
        )

        override suspend fun clearForCleanup(identity: SessionRuntimeIdentity) {
            reminders.removeAll { reminder ->
                reminder.taskIdentity.namespaceId == identity.scopeKey.namespaceId() &&
                    reminder.taskIdentity.sessionEpoch == identity.sessionEpoch
            }
            processed.keys.removeAll { task ->
                task.namespaceId == identity.scopeKey.namespaceId() &&
                    task.sessionEpoch == identity.sessionEpoch
            }
        }
    }

    private companion object {
        const val TEST_WORK_TAG = "service_time_end_work_v2"
        val ACTIVE_WORK_STATES = setOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED,
        )
    }
}
