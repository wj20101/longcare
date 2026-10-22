package com.ytone.longcare.features.home.vm

import com.ytone.longcare.domain.home.HomeDeviceRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDeviceViewModelTest {
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `visible home fetches immediately and repeats after five seconds`() = runTest {
        val repository = RecordingRepository()
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }

        runCurrent()
        assertEquals(1, repository.calls)
        assertEquals(STATUS, viewModel.deviceStatus.value)

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, repository.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.calls)
        polling.cancelAndJoin()
    }

    @Test
    fun `two second requests still start five seconds apart`() = runTest {
        val repository = RecordingRepository {
            delay(2_000)
            ApiResult.Success(STATUS)
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(1, repository.calls)
        assertNull(viewModel.deviceStatus.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(STATUS, viewModel.deviceStatus.value)
        assertEquals(0, repository.activeRequests)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(1, repository.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.calls)
        assertEquals(1, repository.activeRequests)
        assertEquals(1, repository.maximumActiveRequests)
        polling.cancelAndJoin()
    }

    @Test
    fun `unknown logged out and sales sessions never fetch`() = runTest {
        val repository = RecordingRepository()
        val session = TestSessionRepository(SessionState.Unknown)
        val viewModel = HomeDeviceViewModel(repository, session)
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }

        for (state in listOf(SessionState.Unknown, SessionState.LoggedOut, salesSession())) {
            session.sessionState.value = state
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(0, repository.calls)
            assertNull(viewModel.deviceStatus.value)
        }
        polling.cancelAndJoin()
    }

    @Test
    fun `stopping polling retains status and slow return refresh never hides it`() = runTest {
        val refreshed = STATUS.copy(status = "新状态")
        val repository = RecordingRepository { call ->
            if (call == 1) ApiResult.Success(STATUS) else {
                delay(2_000)
                ApiResult.Success(refreshed)
            }
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val first = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(STATUS, viewModel.deviceStatus.value)

        first.cancelAndJoin()
        assertEquals(STATUS, viewModel.deviceStatus.value)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, repository.calls)

        val resumed = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(2, repository.calls)
        assertEquals(STATUS, viewModel.deviceStatus.value)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(STATUS, viewModel.deviceStatus.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(refreshed, viewModel.deviceStatus.value)
        resumed.cancelAndJoin()
        assertEquals(refreshed, viewModel.deviceStatus.value)
    }

    @Test
    fun `hidden page clears retained status on logout sales account or token change without fetching`() = runTest {
        val session = TestSessionRepository()
        val repository = RecordingRepository()
        val viewModel = HomeDeviceViewModel(repository, session)
        val changes = listOf(
            SessionState.LoggedOut,
            salesSession(),
            careSession(200),
            careSession().copy(user = careSession().user.copy(token = "renewed-token")),
            careSession().copy(user = careSession().user.copy(companyId = 11)),
            careSession().copy(user = careSession().user.copy(accountId = 21)),
        )
        changes.forEachIndexed { index, changed ->
            session.sessionState.value = careSession()
            val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
            runCurrent()
            assertEquals(STATUS, viewModel.deviceStatus.value)
            polling.cancelAndJoin()
            session.sessionState.value = changed
            runCurrent()
            assertNull(viewModel.deviceStatus.value)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(index + 1, repository.calls)
        }
    }

    @Test
    fun `profile changes retain status without restarting polling`() = runTest {
        val session = TestSessionRepository()
        val repository = RecordingRepository()
        val viewModel = HomeDeviceViewModel(repository, session)
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        session.sessionState.value = careSession().copy(user = careSession().user.copy(userName = "新名字", headUrl = "new-avatar"))
        runCurrent()
        assertEquals(STATUS, viewModel.deviceStatus.value)
        assertEquals(1, repository.calls)
        polling.cancelAndJoin()
    }

    @Test
    fun `stopping polling cancels the in flight request`() = runTest {
        var finished = false
        val repository = RecordingRepository {
            delay(10_000)
            finished = true
            ApiResult.Success(STATUS)
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(1, repository.activeRequests)

        polling.cancelAndJoin()
        advanceTimeBy(20_000)
        runCurrent()
        assertFalse(finished)
        assertEquals(0, repository.activeRequests)
        assertEquals(1, repository.calls)
        assertNull(viewModel.deviceStatus.value)
    }

    @Test
    fun `switching accounts clears immediately and rejects a late old response`() = runTest {
        val lateResponse = CompletableDeferred<Unit>()
        val oldStatus = STATUS.copy(status = "旧账号状态")
        val lateOldStatus = STATUS.copy(status = "旧账号迟到状态")
        val newStatus = STATUS.copy(deviceSn = "new-device", status = "新账号状态")
        val session = TestSessionRepository()
        val repository = RecordingRepository { call ->
            when (call) {
                1 -> ApiResult.Success(oldStatus)
                2 -> withContext(NonCancellable) {
                    lateResponse.await()
                    ApiResult.Success(lateOldStatus)
                }
                else -> ApiResult.Success(newStatus)
            }
        }
        val viewModel = HomeDeviceViewModel(repository, session)
        val published = mutableListOf<ZhiChiDeviceStatusModel?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.deviceStatus.collect { published += it }
        }
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(oldStatus, viewModel.deviceStatus.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, repository.calls)

        session.sessionState.value = careSession(userId = 200)
        runCurrent()
        assertNull(viewModel.deviceStatus.value)
        lateResponse.complete(Unit)
        runCurrent()

        assertEquals(3, repository.calls)
        assertEquals(newStatus, viewModel.deviceStatus.value)
        assertFalse(published.contains(lateOldStatus))
        polling.cancelAndJoin()
    }

    @Test
    fun `changing to sales or logging out clears status and stops requests`() = runTest {
        val repository = RecordingRepository()
        val session = TestSessionRepository()
        val viewModel = HomeDeviceViewModel(repository, session)
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(1, repository.calls)

        session.sessionState.value = salesSession()
        runCurrent()
        assertNull(viewModel.deviceStatus.value)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, repository.calls)

        session.sessionState.value = careSession()
        runCurrent()
        assertEquals(2, repository.calls)
        session.sessionState.value = SessionState.LoggedOut
        runCurrent()
        assertNull(viewModel.deviceStatus.value)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(2, repository.calls)
        polling.cancelAndJoin()
    }

    @Test
    fun `failures retain the last successful state and later success replaces it`() = runTest {
        val recovered = STATUS.copy(status = "已恢复")
        val repository = RecordingRepository { call ->
            when (call) {
                1 -> ApiResult.Success(STATUS)
                2 -> ApiResult.Failure(500, "暂时不可用")
                3 -> ApiResult.Exception(IOException("network failure"))
                4 -> throw IOException("connection failure")
                else -> ApiResult.Success(recovered)
            }
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(STATUS, viewModel.deviceStatus.value)

        repeat(3) {
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(STATUS, viewModel.deviceStatus.value)
        }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5, repository.calls)
        assertEquals(recovered, viewModel.deviceStatus.value)
        polling.cancelAndJoin()
    }

    @Test
    fun `requests slower than five seconds restart only after completion without overlapping`() = runTest {
        val repository = RecordingRepository {
            delay(12_000)
            ApiResult.Success(STATUS)
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        advanceTimeBy(11_999)
        runCurrent()
        assertEquals(1, repository.calls)
        assertEquals(1, repository.activeRequests)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.calls)
        assertEquals(1, repository.activeRequests)
        assertEquals(1, repository.maximumActiveRequests)
        polling.cancelAndJoin()
    }

    @Test
    fun `duplicate polling callers do not create concurrent loops`() = runTest {
        val repository = RecordingRepository()
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val first = backgroundScope.launch { viewModel.pollDeviceStatus() }
        val duplicate = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        assertEquals(1, repository.calls)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, repository.calls)

        first.cancelAndJoin()
        runCurrent()
        assertEquals(3, repository.calls)
        assertEquals(1, repository.maximumActiveRequests)
        duplicate.cancelAndJoin()
        assertEquals(STATUS, viewModel.deviceStatus.value)
    }

    @Test
    fun `wrapped cancellation does not become a periodic retry`() = runTest {
        val repository = RecordingRepository {
            ApiResult.Exception(CancellationException("cancelled request"))
        }
        val viewModel = HomeDeviceViewModel(repository, TestSessionRepository())
        val polling = backgroundScope.launch { viewModel.pollDeviceStatus() }
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(1, repository.calls)
        assertNull(viewModel.deviceStatus.value)
        assertEquals(0, repository.activeRequests)
        polling.cancelAndJoin()
    }

    private class RecordingRepository(
        private val response: suspend (Int) -> ApiResult<ZhiChiDeviceStatusModel> = {
            ApiResult.Success(STATUS)
        },
    ) : HomeDeviceRepository {
        var calls = 0
        var activeRequests = 0
        var maximumActiveRequests = 0

        override suspend fun getDeviceStatus(): ApiResult<ZhiChiDeviceStatusModel> {
            calls++
            activeRequests++
            maximumActiveRequests = maxOf(maximumActiveRequests, activeRequests)
            return try {
                response(calls)
            } finally {
                activeRequests--
            }
        }
    }

    private class TestSessionRepository(initial: SessionState = careSession()) : UserSessionRepository {
        override val sessionState = MutableStateFlow(initial)
        // This UI/probe fake never authenticates real network requests.
        override val sessionSnapshot = kotlinx.coroutines.flow.MutableStateFlow(com.ytone.longcare.model.UserSessionSnapshot(0L, null))
        override suspend fun logoutIfCurrent(generation: Long): Long? = null

        override suspend fun login(user: User) {
            sessionState.value = SessionState.LoggedIn(user)
        }

        override suspend fun updateUser(user: User) = login(user)

        override suspend fun logout() {
            sessionState.value = SessionState.LoggedOut
        }
    }

    private companion object {
        val STATUS = ZhiChiDeviceStatusModel(
            deviceSn = "test-device",
            status = "在线",
            statusColor = "#00AA77",
            installUrl = "https://example.com/install",
            previewUrl = "https://example.com/preview",
        )

        fun careSession(userId: Int = 100): SessionState.LoggedIn = SessionState.LoggedIn(
            User(companyId = 10, accountId = 20, userId = userId, userIdentity = 1, token = "test-token-$userId"),
        )

        fun salesSession() = SessionState.LoggedIn(User(userId = 300, userIdentity = 2))
    }
}
