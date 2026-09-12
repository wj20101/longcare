package com.ytone.longcare.assistant

import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantSessionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = mockk<UserSessionRepository>(relaxed = true)
    private val invalidation = mockk<SessionInvalidationHandler>(relaxed = true)
    private val cleaner = mockk<AssistantPhotoCleaner>(relaxed = true)

    private fun model(state: SavedStateHandle = SavedStateHandle()): AssistantSessionViewModel {
        every { repository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { invalidation.invalidations } returns MutableStateFlow(null)
        return AssistantSessionViewModel(state, repository, invalidation, cleaner)
    }

    @Test fun `only real network face tools require login`() {
        assertEquals(setOf(AssistantTool.DEFAULT_FACE, AssistantTool.TENCENT_FACE), AssistantTool.entries.filter { it.requiresLogin }.toSet())
        assertEquals(5, AssistantTool.entries.size)
    }

    @Test fun `pending order survives recreation and is consumed once`() {
        val state = SavedStateHandle()
        val expected = AssistantToolRoute(AssistantTool.DEFAULT_FACE, 123)
        model(state).requireLogin(expected)
        val recreated = model(SavedStateHandle(mapOf("pendingTool" to state.get<String>("pendingTool"), "pendingOrderId" to state.get<Long>("pendingOrderId"))))
        assertEquals(expected, recreated.takePending())
        assertNull(recreated.takePending())
    }

    @Test fun `cancel login clears destination`() {
        val model = model()
        model.requireLogin(AssistantToolRoute(AssistantTool.TENCENT_FACE))
        model.cancelPending()
        assertNull(model.takePending())
    }

    @Test fun `logout clears assistant results and only invokes its repository`() = runTest {
        val model = model()
        model.report("result")
        model.showPhoto("file:///private-photo.jpg")
        model.requireLogin(AssistantToolRoute(AssistantTool.TENCENT_FACE))
        model.logout()
        advanceUntilIdle()
        assertEquals("", model.result.value)
        assertEquals("", model.photoUri.value)
        assertNull(model.takePending())
        coVerify(exactly = 1) { repository.logout() }
        verify(exactly = 1) { cleaner.discard("file:///private-photo.jpg") }
    }

    @Test fun `replacing a photo deletes only the previous photo and identical callbacks are harmless`() {
        val model = model()
        model.showPhoto("file:///old.jpg")
        model.showPhoto("file:///old.jpg")
        verify(exactly = 0) { cleaner.discard("file:///old.jpg") }
        model.showPhoto("file:///new.jpg")
        assertEquals("file:///new.jpg", model.photoUri.value)
        verify(exactly = 1) { cleaner.discard("file:///old.jpg") }
        verify(exactly = 0) { cleaner.discard("file:///new.jpg") }
    }

    @Test fun `clearing a restored result deletes its managed photo once`() {
        val model = model(SavedStateHandle(mapOf("photoUri" to "file:///restored.jpg", "result" to "old result")))
        model.clearResult()
        model.clearResult()
        assertEquals("", model.photoUri.value)
        assertEquals("", model.result.value)
        verify(exactly = 1) { cleaner.discard("file:///restored.jpg") }
    }
}
