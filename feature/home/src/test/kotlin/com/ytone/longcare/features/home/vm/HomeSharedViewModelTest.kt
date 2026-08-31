package com.ytone.longcare.features.home.vm

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.api.HomeExperience
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.UserScopeKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSharedViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reportHomeEntry sends one log payload`() = runTest {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(
            phoneSystem = "Android",
            phoneVersion = "16",
            networkType = "WIFI",
            networkOperator = "Carrier",
        )

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        } returns Unit

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        }
    }

    @Test
    fun `reportHomeEntry ignores concurrent duplicate call while request in flight`() = runTest {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        } coAnswers {
            delay(1_000)
        }

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        }
    }

    @Test
    fun `reportHomeEntry remains idempotent after request completes`() = runTest {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        } returns Unit

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        advanceUntilIdle()
        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        }
    }

    @Test
    fun `reportHomeEntry swallows repository failure`() = runTest {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        } throws IllegalStateException("boom")

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        }
    }

    @Test
    fun `ui state resolves roles preserves same-user tab and resets it on account switch`() = runTest {
        val sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
        val viewModel = createViewModel(sessionState)

        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(HomeUiState())

        val salesUser = currentUser(userId = 100, role = 2)
        sessionState.value = SessionState.LoggedIn(salesUser)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.user).isEqualTo(salesUser)
        assertThat(viewModel.uiState.value.experience).isEqualTo(HomeExperience.Sales)

        viewModel.selectDashboardTab(1)
        sessionState.value = SessionState.LoggedIn(salesUser.copy(userIdentity = 1))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.experience).isEqualTo(HomeExperience.Care)
        assertThat(viewModel.uiState.value.selectedDashboardTab).isEqualTo(1)

        val nextUser = currentUser(userId = 101, role = 1)
        sessionState.value = SessionState.LoggedIn(nextUser)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.user).isEqualTo(nextUser)
        assertThat(viewModel.uiState.value.selectedDashboardTab).isEqualTo(0)

        sessionState.value = SessionState.LoggedOut
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.user).isNull()
        assertThat(viewModel.uiState.value.experience).isEqualTo(HomeExperience.Loading)
    }

    @Test
    fun `clearing graph ViewModel stops session collection`() = runTest {
        val sessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
        val viewModel = createViewModel(sessionState)
        val store = ViewModelStore()
        store.put("home", viewModel)
        advanceUntilIdle()

        store.clear()
        sessionState.value = SessionState.LoggedIn(currentUser(userId = 200, role = 2))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(HomeUiState())
    }

    @Test
    fun `recordHomeEntry rethrows coroutine cancellation`() = runTest {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")
        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery { loginRepository.recordLoginLog(any(), any(), any(), any()) } throws
            CancellationException("cancelled")
        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        val failure = runCatching { viewModel.recordHomeEntry() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
    }

    private fun createViewModel(sessionState: MutableStateFlow<SessionState>): HomeSharedViewModel {
        val sessionRepository = mockk<UserSessionRepository>()
        every { sessionRepository.sessionState } returns sessionState
        return HomeSharedViewModel(
            userSessionRepository = sessionRepository,
            loginRepository = mockk(relaxed = true),
            homeLoginLogInfoProvider = mockk(relaxed = true),
        )
    }

    private fun currentUser(userId: Int, role: Int) = CurrentUser(
        scopeKey = UserScopeKey(companyId = 1, accountId = 10, userId = userId),
        userName = "user-$userId",
        headUrl = "",
        userIdentity = role,
        gender = 0,
    )
}
