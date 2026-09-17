package com.ytone.longcare.assistant

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.network.SessionInvalidation
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import com.ytone.longcare.theme.LongCareTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AssistantSessionNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantActivity>()

    @Test fun login_restores_order_destination_once_and_cancellation_clears_pending() {
        val repository = object : UserSessionRepository {
            override val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)
            override suspend fun login(user: User) { sessionState.value = SessionState.LoggedIn(user) }
            override suspend fun updateUser(user: User) { login(user) }
            override suspend fun logout() { sessionState.value = SessionState.LoggedOut }
        }
        val invalidation = object : SessionInvalidationHandler {
            override val invalidations = MutableStateFlow<SessionInvalidation?>(null)
            override fun invalidate(reason: String) { error("No network invalidation expected") }
            override fun consume(id: Long) { invalidations.value = null }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = AssistantSessionViewModel(SavedStateHandle(), repository, invalidation,
            AssistantPhotoCleaner(UnifiedImagePipeline(compose.activity.applicationContext, Dispatchers.IO), scope))
        val store = ViewModelStore().apply { put("test-session", vm) }
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent { LongCareTheme { AssistantNavigation(activity, activity.nfcTestHelper, vm) } }
            }
            compose.onNodeWithText("默认服务人脸验证").performScrollTo().performClick()
            compose.onNodeWithText("手机号码").assertExists()
            // Publish the authenticated session without calling SMS or a face endpoint.
            compose.runOnIdle { repository.sessionState.value = SessionState.LoggedIn(User(userId = 42)) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("开始验证").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertNull(vm.takePending()) }
            compose.onNodeWithText("服务订单 ID（1–2147483647）").assertExists()
            // Invalidate a protected page: its owner must go away and the target resumes once.
            compose.runOnIdle { repository.sessionState.value = SessionState.LoggedOut }
            compose.onNodeWithText("手机号码").assertExists()
            compose.runOnIdle { repository.sessionState.value = SessionState.LoggedIn(User(userId = 42)) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("开始验证").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertNull(vm.takePending()) }
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
            compose.runOnIdle { repository.sessionState.value = SessionState.LoggedOut }
            compose.onNodeWithText("备用腾讯人脸验证").performScrollTo().performClick()
            compose.onNodeWithText("手机号码").assertExists()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
            compose.runOnIdle { assertNull(vm.takePending()) }
        } finally {
            compose.runOnIdle { store.clear(); scope.cancel() }
        }
    }
}
