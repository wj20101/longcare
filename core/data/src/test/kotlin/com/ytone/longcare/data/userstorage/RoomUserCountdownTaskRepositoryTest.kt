package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.COUNTDOWN_TASK_TYPE
import com.ytone.longcare.domain.userstorage.PendingCountdownTask
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.domain.userstorage.countdownBusinessId
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomUserCountdownTaskRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pathsFactory = UserNamespacePathsFactory(context)
    private val scopes = mutableSetOf<UserScopeKey>()

    @After
    fun cleanUp() {
        scopes.forEach { scope ->
            pathsFactory.forScope(scope).also { paths ->
                context.deleteDatabase(paths.databaseFile.name)
                paths.dataStoreFile.delete()
                paths.namespaceRoot.deleteRecursively()
                paths.sessionRoot.parentFile?.deleteRecursively()
            }
        }
    }

    @Test
    fun `same order is isolated by user namespace and includes plan in task identity`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = scope(1, 2, 3)
        val scopeB = scope(1, 2, 4)
        val order = OrderKey(orderId = 55, planId = 7)
        val leaseA = fixture.registry.open(scopeA, SessionEpoch(10))
        fixture.countdownRepository.upsert(task(leaseA, order, "A"))

        val leaseB = fixture.registry.open(scopeB, SessionEpoch(20))
        fixture.countdownRepository.upsert(task(leaseB, order, "B"))
        assertEquals(listOf("B"), fixture.countdownRepository.getAllForCurrentSession().map { it.serviceName })

        fixture.registry.open(scopeA, SessionEpoch(10))
        val restoredA = fixture.countdownRepository.getAllForCurrentSession().single()
        assertEquals("A", restoredA.serviceName)
        assertEquals("55:7", restoredA.taskIdentity.businessId)
        assertTrue(restoredA.taskIdentity != fixture.countdownRepository.run {
            fixture.registry.open(scopeB, SessionEpoch(20))
            getAllForCurrentSession().single().taskIdentity
        })
        fixture.registry.close()
    }

    @Test
    fun `countdown cleanup is exact and does not delete service reminders`() = runTest {
        val fixture = fixture(backgroundScope)
        val scope = scope(11, 12, 13)
        val lease = fixture.registry.open(scope, SessionEpoch(30))
        val countdown = task(lease, OrderKey(99, 2), "countdown")
        fixture.countdownRepository.upsert(countdown)
        fixture.serviceRepository.upsert(
            PendingServiceReminder(
                taskIdentity = UserTaskIdentity(
                    namespaceId = scope.namespaceId(),
                    sessionEpoch = lease.sessionEpoch,
                    taskType = SERVICE_TIME_END_TASK_TYPE,
                    businessId = "99",
                ),
                orderId = 99,
                serviceName = "service-reminder",
                triggerAtMillis = 4_000,
            ),
        )

        val runtimeIdentity = SessionRuntimeIdentity(scope, lease.sessionEpoch)
        fixture.registry.revoke()
        assertEquals(listOf(countdown), fixture.countdownRepository.snapshotForCleanup(runtimeIdentity))
        fixture.countdownRepository.clearForCleanup(runtimeIdentity)
        assertTrue(fixture.countdownRepository.snapshotForCleanup(runtimeIdentity).isEmpty())
        assertEquals(
            listOf("service-reminder"),
            fixture.serviceRepository.snapshotForCleanup(runtimeIdentity)
                .pendingReminders
                .map { it.serviceName },
        )
        fixture.registry.close()
    }

    private fun fixture(applicationScope: kotlinx.coroutines.CoroutineScope): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        val access = UserDatabaseAccess(registry)
        return Fixture(
            registry = registry,
            countdownRepository = RoomUserCountdownTaskRepository(access, registry),
            serviceRepository = RoomUserServiceReminderRepository(access, registry),
        )
    }

    private fun task(
        lease: com.ytone.longcare.domain.userstorage.UserStorageLease,
        orderKey: OrderKey,
        serviceName: String,
    ) = PendingCountdownTask(
        taskIdentity = UserTaskIdentity(
            namespaceId = lease.scopeKey.namespaceId(),
            sessionEpoch = lease.sessionEpoch,
            taskType = COUNTDOWN_TASK_TYPE,
            businessId = countdownBusinessId(orderKey),
        ),
        generation = lease.generation,
        orderKey = orderKey,
        serviceName = serviceName,
        triggerAtMillis = 5_000,
    )

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val countdownRepository: RoomUserCountdownTaskRepository,
        val serviceRepository: RoomUserServiceReminderRepository,
    )
}
