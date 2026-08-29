package com.ytone.longcare.data.userstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.ytone.longcare.domain.userstorage.PendingServiceReminder
import com.ytone.longcare.domain.userstorage.SERVICE_TIME_END_TASK_TYPE
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserTaskIdentity
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomUserServiceReminderRepositoryTest {
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
    fun `same order ID is physically isolated and logged out access fails closed`() = runTest {
        val fixture = fixture(backgroundScope)
        val scopeA = scope(1, 2, 3)
        val scopeB = scope(1, 2, 4)
        fixture.registry.open(scopeA, SessionEpoch(10))
        fixture.repository.upsert(reminder(scopeA, epoch = 10, serviceName = "A"))

        fixture.registry.open(scopeB, SessionEpoch(20))
        fixture.repository.upsert(reminder(scopeB, epoch = 20, serviceName = "B"))
        assertEquals(listOf("B"), fixture.repository.getAllForCurrentSession().map { it.serviceName })

        fixture.registry.open(scopeA, SessionEpoch(10))
        assertEquals(listOf("A"), fixture.repository.getAllForCurrentSession().map { it.serviceName })
        fixture.registry.close()

        assertTrue(runCatching { fixture.repository.getAllForCurrentSession() }.isFailure)
    }

    @Test
    fun `revoked cleanup snapshot and deletion are limited to the exact epoch`() = runTest {
        val fixture = fixture(backgroundScope)
        val scope = scope(11, 12, 13)
        fixture.registry.open(scope, SessionEpoch(30))
        val reminder = reminder(scope, epoch = 30, serviceName = "current")
        fixture.repository.upsert(reminder)
        fixture.repository.markProcessed(reminder.taskIdentity, processedAtMillis = 1_000)
        assertTrue(fixture.repository.wasProcessedSince(reminder.taskIdentity, sinceMillis = 999))

        val runtimeIdentity = SessionRuntimeIdentity(scope, SessionEpoch(30))
        fixture.registry.revoke()
        val before = fixture.repository.snapshotForCleanup(runtimeIdentity)
        fixture.repository.clearForCleanup(runtimeIdentity)
        val after = fixture.repository.snapshotForCleanup(runtimeIdentity)

        assertEquals(listOf(55L), before.pendingReminders.map { it.orderId })
        assertEquals(setOf(55L), before.processedOrderIds)
        assertTrue(after.pendingReminders.isEmpty())
        assertTrue(after.processedOrderIds.isEmpty())
        fixture.registry.close()
    }

    @Test
    fun `stale epoch cannot mutate the current reminder table`() = runTest {
        val fixture = fixture(backgroundScope)
        val scope = scope(21, 22, 23)
        fixture.registry.open(scope, SessionEpoch(40))

        val failure = runCatching {
            fixture.repository.upsert(reminder(scope, epoch = 39, serviceName = "stale"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(fixture.repository.getAllForCurrentSession().any())
        fixture.registry.close()
    }

    private fun fixture(applicationScope: kotlinx.coroutines.CoroutineScope): Fixture {
        val registry = UserStorageRegistry(
            pathsFactory = pathsFactory,
            metadataStore = UserNamespaceMetadataStore(Moshi.Builder().build()),
            dataStoreRegistry = UserDataStoreRegistry(applicationScope),
            databaseFactory = UserDatabaseFactory(context, Dispatchers.IO, pathsFactory),
        )
        return Fixture(
            registry,
            RoomUserServiceReminderRepository(UserDatabaseAccess(registry), registry),
        )
    }

    private fun reminder(
        scope: UserScopeKey,
        epoch: Long,
        serviceName: String,
    ): PendingServiceReminder {
        val identity = UserTaskIdentity(
            namespaceId = scope.namespaceId(),
            sessionEpoch = SessionEpoch(epoch),
            taskType = SERVICE_TIME_END_TASK_TYPE,
            businessId = "55",
        )
        return PendingServiceReminder(identity, 55, serviceName, triggerAtMillis = 5_000)
    }

    private fun scope(companyId: Int, accountId: Int, userId: Int) =
        UserScopeKey(companyId, accountId, userId).also(scopes::add)

    private data class Fixture(
        val registry: UserStorageRegistry,
        val repository: RoomUserServiceReminderRepository,
    )
}
