package com.ytone.longcare.features.service.receiver

import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
}
