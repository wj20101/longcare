package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSessionInvalidationHandlerTest {

    @Test
    fun `session is cleared and notification remains until UI consumes it`() = runTest {
        val repository = FakeUserSessionRepository(loggedInUser(token = "token-a"))
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        runCurrent()

        handler.invalidate("登录过期")

        assertEquals(1, repository.logoutCalls)
        val invalidation = handler.invalidations.value
        assertNotNull(invalidation)
        assertEquals("登录过期", invalidation?.reason)

        handler.consume(invalidation!!.id)
        assertNull(handler.invalidations.value)
    }

    @Test
    fun `repeated 3002 responses clear the same session only once`() = runTest {
        val repository = FakeUserSessionRepository(loggedInUser(token = "token-a"))
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        runCurrent()

        handler.invalidate("第一次")
        val firstId = handler.invalidations.value?.id
        handler.invalidate("第二次")

        assertEquals(1, repository.logoutCalls)
        assertEquals(firstId, handler.invalidations.value?.id)
        assertEquals("第一次", handler.invalidations.value?.reason)
    }

    @Test
    fun `a newly logged in session can be invalidated again`() = runTest {
        val repository = FakeUserSessionRepository(loggedInUser(token = "token-a"))
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        runCurrent()

        handler.invalidate("旧会话过期")
        repository.setState(SessionState.LoggedOut)
        runCurrent()
        repository.setState(loggedInUser(token = "token-b"))
        runCurrent()

        handler.invalidate("新会话过期")

        assertEquals(2, repository.logoutCalls)
        assertEquals("新会话过期", handler.invalidations.value?.reason)
    }

    @Test
    fun `3002 without an active session does not create logout work`() = runTest {
        val repository = FakeUserSessionRepository(SessionState.LoggedOut)
        val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
        runCurrent()

        handler.invalidate("登录过期")

        assertEquals(0, repository.logoutCalls)
        assertNull(handler.invalidations.value)
    }
}

private class FakeUserSessionRepository(
    initialState: SessionState,
) : UserSessionRepository {
    private val mutableSessionState = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = mutableSessionState
    var logoutCalls: Int = 0
        private set

    override fun login(user: User) {
        mutableSessionState.value = SessionState.LoggedIn(user)
    }

    override fun updateUser(user: User) {
        mutableSessionState.value = SessionState.LoggedIn(user)
    }

    override fun logout() {
        logoutCalls += 1
    }

    fun setState(state: SessionState) {
        mutableSessionState.value = state
    }
}

private fun loggedInUser(token: String): SessionState =
    SessionState.LoggedIn(
        User(
            userId = 7,
            token = token,
        )
    )
