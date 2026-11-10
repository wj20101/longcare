package com.ytone.longcare

import androidx.lifecycle.ViewModelStore
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelUpdateTest {
    @get:Rule val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())
    private val sessions = mockk<UserSessionRepository>()
    private val system = mockk<SystemRepository>()
    private val device = mockk<DeviceUtils>()
    private val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    private val stores = mutableListOf<ViewModelStore>()
    private val update = AppVersionModel(versionCode = 82, versionName = "1.0.7", platform = "android")

    @Before fun setUp() {
        every { sessions.sessionState } returns sessionState
        every { device.getAppVersionCode() } returns 81L
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "42"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } just runs
    }

    @After fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        unmockkObject(DiagnosticEventTracker)
    }

    private fun model(): MainViewModel = MainViewModel(sessions, system, device).also { vm ->
        stores += ViewModelStore().apply { put("main", vm) }
    }

    @Test fun `constructing before the consented entry does not check updates`() = runTest {
        model()
        runCurrent()
        coVerify(exactly = 0) { system.checkVersion() }
    }

    @Test fun `repeated entry calls share one startup request and dismissal stays dismissed`() = runTest {
        val response = CompletableDeferred<ApiResult<AppVersionModel?>>()
        coEvery { system.checkVersion() } coAnswers { response.await() }
        val model = model()
        repeat(5) { model.checkForUpdateOnStartup() }
        runCurrent()
        coVerify(exactly = 1) { system.checkVersion() }
        response.complete(ApiResult.Success(update))
        runCurrent()
        assertEquals(update, model.appVersionModel.value)

        model.clearAppVersionModel()
        repeat(5) { model.checkForUpdateOnStartup() }
        advanceTimeBy(300_000)
        runCurrent()
        assertNull(model.appVersionModel.value)
        coVerify(exactly = 1) { system.checkVersion() }
    }

    @Test fun `same version name with a greater version code still prompts`() = runTest {
        coEvery { system.checkVersion() } returns ApiResult.Success(update)
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        assertEquals("1.0.7", model.appVersionModel.value?.versionName)
        assertEquals(82, model.appVersionModel.value?.versionCode)
    }

    @Test fun `equal or older codes do not prompt even with a different version name`() = runTest {
        for (code in listOf(80, 81)) {
            coEvery { system.checkVersion() } returns ApiResult.Success(update.copy(versionCode = code, versionName = "9.0.0"))
            val model = model()
            model.checkForUpdateOnStartup()
            runCurrent()
            assertNull(model.appVersionModel.value)
        }
    }

    @Test fun `empty version and non Android platform do not prompt or report errors`() = runTest {
        for (version in listOf(null, update.copy(platform = "ios"))) {
            coEvery { system.checkVersion() } returns ApiResult.Success(version)
            val model = model()
            model.checkForUpdateOnStartup()
            runCurrent()
            assertNull(model.appVersionModel.value)
        }
        verify(exactly = 0) { device.getAppVersionCode() }
        verify(exactly = 0) { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `transient failure is recorded once and never retried during use`() = runTest {
        val error = mockk<ApiRequestException>()
        every { error.kind } returns ApiRequestException.Kind.HTTP
        every { error.httpCode } returns 503
        coEvery { system.checkVersion() } returns ApiResult.Exception(error)
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        advanceTimeBy(300_000)
        model.checkForUpdateOnStartup()
        runCurrent()

        assertNull(model.appVersionModel.value)
        coVerify(exactly = 1) { system.checkVersion() }
        verify(exactly = 1) {
            DiagnosticEventTracker.trackError(
                category = DiagnosticCategory.APP_UPDATE,
                event = "version_check_exception",
                description = "版本检查接口异常",
                throwable = error,
                extras = mapOf("attempt" to 1, "errorKind" to "HTTP", "httpCode" to 503, "willRetry" to false),
                userId = "42",
            )
        }
    }

    @Test fun `a new launch can check again after an offline launch`() = runTest {
        coEvery { system.checkVersion() } returns ApiResult.Exception(IOException("offline"))
        val first = model()
        first.checkForUpdateOnStartup()
        runCurrent()
        coEvery { system.checkVersion() } returns ApiResult.Success(update)
        first.checkForUpdateOnStartup()
        val next = model()
        next.checkForUpdateOnStartup()
        runCurrent()

        assertNull(first.appVersionModel.value)
        assertEquals(update, next.appVersionModel.value)
        coVerify(exactly = 2) { system.checkVersion() }
    }

    @Test fun `login and logout do not trigger additional checks`() = runTest {
        coEvery { system.checkVersion() } returns ApiResult.Success(null)
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        sessionState.value = SessionState.LoggedIn(User(userId = 42))
        model.checkForUpdateOnStartup()
        runCurrent()
        sessionState.value = SessionState.LoggedOut
        runCurrent()
        coVerify(exactly = 1) { system.checkVersion() }
    }

    @Test fun `business failure does not become an update or another request`() = runTest {
        coEvery { system.checkVersion() } returns ApiResult.Failure(400, "not available")
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        model.checkForUpdateOnStartup()
        runCurrent()
        assertNull(model.appVersionModel.value)
        coVerify(exactly = 1) { system.checkVersion() }
    }

    @Test fun `unexpected repository exception ends the check without crashing or retrying`() = runTest {
        coEvery { system.checkVersion() } throws IOException("connection lost")
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        model.checkForUpdateOnStartup()
        runCurrent()
        assertNull(model.appVersionModel.value)
        coVerify(exactly = 1) { system.checkVersion() }
    }

    @Test fun `cancelled check does not report an error or retry`() = runTest {
        coEvery { system.checkVersion() } throws CancellationException("entry closed")
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        model.checkForUpdateOnStartup()
        runCurrent()
        assertNull(model.appVersionModel.value)
        coVerify(exactly = 1) { system.checkVersion() }
        verify(exactly = 0) { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `a late response cannot publish after the activity model is cleared`() = runTest {
        val response = CompletableDeferred<ApiResult<AppVersionModel?>>()
        coEvery { system.checkVersion() } coAnswers { withContext(NonCancellable) { response.await() } }
        val model = model()
        model.checkForUpdateOnStartup()
        runCurrent()
        stores.last().clear()
        response.complete(ApiResult.Success(update))
        runCurrent()
        assertNull(model.appVersionModel.value)
    }
}
