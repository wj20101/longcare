package com.ytone.longcare.data.userstorage

import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageState
import com.ytone.longcare.domain.userstorage.validateAgainst
import com.ytone.longcare.model.UserScopeKey
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserDatabaseAccessTest {
    private val leaseA = lease(userId = 1, epoch = 11, generation = 1)
    private val leaseB = lease(userId = 2, epoch = 12, generation = 2)
    private val databaseA = mockk<LongCareDatabase>(relaxed = true)
    private val databaseB = mockk<LongCareDatabase>(relaxed = true)

    @Test
    fun `logged out and opening states reject database access`() = runTest {
        val source = FakeLeaseSource(databaseA, databaseB)
        val access = UserDatabaseAccess(source)

        assertUnavailable { access.withCurrentLease { _, _ -> "never" } }
        source.mutableState.value = UserStorageState.Opening(leaseA)
        assertUnavailable { access.withCurrentLease { _, _ -> "never" } }
    }

    @Test
    fun `ready state executes against current database and stale generation is rejected`() = runTest {
        val source = FakeLeaseSource(databaseA, databaseB)
        val access = UserDatabaseAccess(source)
        source.mutableState.value = UserStorageState.Ready(leaseA)

        val result = access.withCurrentLease { database, lease ->
            assertTrue(database === databaseA)
            assertEquals(leaseA, lease)
            "ready"
        }
        assertEquals("ready", result)

        source.mutableState.value = UserStorageState.Ready(leaseB)
        assertUnavailable { access.withLease(leaseA) { _, _ -> "stale" } }
    }

    @Test
    fun `observation cancels old database flow when generation changes`() = runTest {
        val source = FakeLeaseSource(databaseA, databaseB)
        val access = UserDatabaseAccess(source)
        val flowA = MutableSharedFlow<String>()
        val flowB = MutableSharedFlow<String>()
        val values = mutableListOf<String>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            access.observeCurrent { database, _ ->
                if (database === databaseA) flowA else flowB
            }.take(2).toList(values)
        }

        source.mutableState.value = UserStorageState.Ready(leaseA)
        yield()
        flowA.emit("A-current")
        source.mutableState.value = UserStorageState.Opening(leaseB)
        yield()
        flowA.emit("A-stale")
        source.mutableState.value = UserStorageState.Ready(leaseB)
        yield()
        flowB.emit("B-current")
        advanceUntilIdle()

        assertEquals(listOf("A-current", "B-current"), values)
        assertTrue(collection.isCompleted)
    }

    private suspend fun <T> assertUnavailable(block: suspend () -> T) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is UserStorageUnavailableException)
    }

    private fun lease(userId: Int, epoch: Long, generation: Long) = UserStorageLease(
        scopeKey = UserScopeKey(companyId = 100, accountId = 200, userId = userId),
        sessionEpoch = SessionEpoch(epoch),
        generation = StorageGeneration(generation),
    )

    private class FakeLeaseSource(
        private val databaseA: LongCareDatabase,
        private val databaseB: LongCareDatabase,
    ) : UserDatabaseLeaseSource {
        val mutableState = MutableStateFlow<UserStorageState>(UserStorageState.LoggedOut)
        override val state = mutableState

        override fun requireCurrentLease(): UserStorageLease =
            (state.value as? UserStorageState.Ready)?.lease
                ?: throw UserStorageUnavailableException("not ready")

        override fun requireValid(lease: UserStorageLease) {
            if (lease.validateAgainst(state.value).let { it !is com.ytone.longcare.domain.userstorage.LeaseValidation.Valid }) {
                throw UserStorageUnavailableException("stale")
            }
        }

        override suspend fun <T> withDatabase(
            lease: UserStorageLease,
            block: suspend (LongCareDatabase) -> T,
        ): T {
            requireValid(lease)
            val database = if (lease.scopeKey.userId == 1) databaseA else databaseB
            return block(database)
        }
    }
}
