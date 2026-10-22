package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserSessionSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSessionInvalidationHandlerTest {
    private val user = User(userId = 7, token = "test-session")

    @Test fun `current invalidation clears once and remains until consumed`() = runTest {
        val repository = FakeUserSessionRepository(user)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        handler.invalidate("first", 1L)
        handler.invalidate("duplicate", 1L)
        runCurrent()
        handler.invalidate("late duplicate", 1L)
        runCurrent()
        assertEquals(1, repository.logoutCalls)
        val event = requireNotNull(handler.invalidations.value)
        assertEquals("first", event.reason)
        assertTrue(handler.consume(event.id))
        assertFalse(handler.consume(event.id))
        assertNull(handler.invalidations.value)
    }

    @Test fun `old account response cannot log out a newer account`() = runTest {
        val repository = FakeUserSessionRepository(user)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        repository.login(User(userId = 8, token = "next-session"))
        handler.invalidate("old response", 1L)
        runCurrent()
        assertEquals(0, repository.logoutCalls)
        assertEquals(8, repository.sessionSnapshot.value.user?.userId)
        assertNull(handler.invalidations.value)
    }

    @Test fun `queued invalidation rechecks inside logout after account switch`() = runTest {
        val repository = FakeUserSessionRepository(user)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        handler.invalidate("queued old response", 1L)
        repository.login(User(userId = 8))
        runCurrent()
        assertEquals(0, repository.logoutCalls)
        assertEquals(8, repository.sessionSnapshot.value.user?.userId)
        assertNull(handler.invalidations.value)
    }

    @Test fun `same user and same token relogin rejects previous generation`() = runTest {
        val repository = FakeUserSessionRepository(user)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        repository.login(user)
        handler.invalidate("old generation", 1L)
        runCurrent()
        assertEquals(0, repository.logoutCalls)
        handler.invalidate("current generation", repository.sessionSnapshot.value.generation)
        runCurrent()
        assertEquals(1, repository.logoutCalls)
    }

    @Test fun `unknown changing and logged out snapshots cannot create logout work`() = runTest {
        val repository = FakeUserSessionRepository(user)
        repository.sessionSnapshot.value = UserSessionSnapshot(2L, null)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        handler.invalidate("no authenticated request", 2L)
        runCurrent()
        assertEquals(0, repository.logoutCalls)
        assertNull(handler.invalidations.value)
    }

    @Test fun `queued UI event is not consumed by a new login even before observer runs`() = runTest {
        val repository = FakeUserSessionRepository(user)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        handler.invalidate("expired", 1L)
        runCurrent()
        val oldEvent = requireNotNull(handler.invalidations.value)
        repository.login(user)
        assertFalse(handler.consume(oldEvent.id))
        assertNull(handler.invalidations.value)
    }

    @Test fun `late logout completion cannot publish a toast to a newer login`() = runTest {
        val repository = FakeUserSessionRepository(user)
        repository.afterLogout = CompletableDeferred()
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        handler.invalidate("expired", 1L)
        runCurrent()
        assertEquals(1, repository.logoutCalls)
        repository.login(user)
        repository.afterLogout!!.complete(Unit)
        runCurrent()
        assertNull(handler.invalidations.value)
        assertEquals(user, repository.sessionSnapshot.value.user)
    }
}

private class FakeUserSessionRepository(user: User?) : UserSessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(user?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut)
    override val sessionSnapshot = MutableStateFlow(UserSessionSnapshot(1L, user))
    var logoutCalls = 0
    var afterLogout: CompletableDeferred<Unit>? = null
    override suspend fun login(user: User) {
        sessionSnapshot.value = UserSessionSnapshot(sessionSnapshot.value.generation + 1, user)
        sessionState.value = SessionState.LoggedIn(user)
    }
    override suspend fun updateUser(user: User) = login(user)
    override suspend fun logout() {
        logoutCalls++
        sessionSnapshot.value = UserSessionSnapshot(sessionSnapshot.value.generation + 1, null)
        sessionState.value = SessionState.LoggedOut
    }
    override suspend fun logoutIfCurrent(generation: Long): Long? {
        if (sessionSnapshot.value.generation != generation || sessionSnapshot.value.user == null) return null
        logout()
        val completedGeneration = sessionSnapshot.value.generation
        afterLogout?.await()
        return completedGeneration
    }
}
