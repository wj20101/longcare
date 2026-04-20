package com.ytone.longcare.features.home.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import com.ytone.longcare.model.LoginLogParamModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeSharedViewModelTest {

    @Test
    fun `reportHomeEntry sends one log payload`() = runTest(StandardTestDispatcher()) {
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
        } returns ApiResult.Success(Unit)

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
    fun `reportHomeEntry ignores concurrent duplicate call while request in flight`() = runTest(StandardTestDispatcher()) {
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
            ApiResult.Success(Unit)
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
    fun `reportHomeEntry swallows repository failure`() = runTest(StandardTestDispatcher()) {
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
}
