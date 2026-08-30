package com.ytone.longcare.features.login.vm

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.LoginPreferencesManager
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.StartConfigResultModel
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.common.text.ResourceTextResolver

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelPrivacyGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init loads startup config immediately`() = runTest {
        val repository = mockk<LoginRepository>(relaxed = true)
        coEvery { repository.getStartConfig() } returns ApiResult.Success(
            StartConfigResultModel(
                userXieYiUrl = "https://example.com/user",
                yinSiXieYiUrl = "https://example.com/privacy"
            )
        )

        val viewModel = createViewModel(repository)

        assertTrue(viewModel.startConfigState.value is StartConfigUiState.Success)
        coVerify(exactly = 1) { repository.getStartConfig() }
    }

    @Test
    fun `startup config is loaded only once regardless of privacy confirmation`() = runTest {
        val repository = mockk<LoginRepository>(relaxed = true)
        coEvery { repository.getStartConfig() } returns ApiResult.Success(
            StartConfigResultModel(
                userXieYiUrl = "https://example.com/user",
                yinSiXieYiUrl = "https://example.com/privacy"
            )
        )

        val viewModel = createViewModel(repository)

        viewModel.onPrivacyAgreementConfirmed()
        viewModel.onPrivacyAgreementConfirmed()

        assertTrue(viewModel.startConfigState.value is StartConfigUiState.Success)
        coVerify(exactly = 1) { repository.getStartConfig() }
    }

    @Test
    fun `send code and login are blocked before privacy confirmation`() = runTest {
        val repository = mockk<LoginRepository>(relaxed = true)
        coEvery { repository.getStartConfig() } returns ApiResult.Success(
            StartConfigResultModel(
                userXieYiUrl = "https://example.com/user",
                yinSiXieYiUrl = "https://example.com/privacy"
            )
        )
        val viewModel = createViewModel(repository)

        viewModel.sendSmsCode("13800138000")
        viewModel.login("13800138000", "123456")

        coVerify(exactly = 0) { repository.sendSmsCode(any()) }
        coVerify(exactly = 0) { repository.login(any(), any()) }
        assertEquals(
            "请先阅读并同意用户协议和隐私政策",
            viewModel.feedback.value?.message
        )
    }

    @Test
    fun `feedback remains until UI consumes the matching event`() = runTest {
        val repository = mockk<LoginRepository>(relaxed = true)
        coEvery { repository.getStartConfig() } returns ApiResult.Success(
            StartConfigResultModel(
                userXieYiUrl = "https://example.com/user",
                yinSiXieYiUrl = "https://example.com/privacy"
            )
        )
        val viewModel = createViewModel(repository)

        viewModel.sendSmsCode("invalid")
        val feedback = requireNotNull(viewModel.feedback.value)

        viewModel.consumeFeedback(feedback.id + 1)
        assertEquals(feedback, viewModel.feedback.value)

        viewModel.consumeFeedback(feedback.id)
        assertNull(viewModel.feedback.value)
    }

    @Test
    fun `last successful login phone is read from login preferences`() = runTest {
        val repository = startupRepository()
        val preferences = mockk<LoginPreferencesManager>()
        every { preferences.getLastLoginPhoneNumber() } returns "13900139000"
        every { preferences.saveLastLoginPhoneNumber(any()) } returns Unit

        val viewModel = createViewModel(
            repository = repository,
            preferences = preferences,
        )

        assertEquals("13900139000", viewModel.getLastLoginPhoneNumber())
        verify(exactly = 1) { preferences.getLastLoginPhoneNumber() }
    }

    @Test
    fun `successful login persists session before phone and success state`() = runTest {
        val repository = startupRepository()
        val sessionRepository = mockk<UserSessionRepository>(relaxed = true)
        val preferences = mockk<LoginPreferencesManager>()
        every { preferences.getLastLoginPhoneNumber() } returns ""
        every { preferences.saveLastLoginPhoneNumber(any()) } returns Unit
        coEvery { repository.login("13800138000", "123456") } returns ApiResult.Success(
            com.ytone.longcare.model.LoginResultModel(
                companyId = 10,
                accountId = 20,
                userId = 30,
                userName = "测试用户",
                token = "session-token",
            )
        )
        val viewModel = createViewModel(
            repository = repository,
            sessionRepository = sessionRepository,
            preferences = preferences,
        )

        viewModel.onPrivacyAgreementConfirmed()
        viewModel.login("13800138000", "123456")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            sessionRepository.login(
                match {
                    it.companyId == 10 &&
                        it.accountId == 20 &&
                        it.userId == 30 &&
                        it.token == "session-token"
                }
            )
        }
        verify(exactly = 1) { preferences.saveLastLoginPhoneNumber("13800138000") }
        assertTrue(viewModel.loginState.value is LoginUiState.Success)
    }

    private fun startupRepository(): LoginRepository =
        mockk<LoginRepository>(relaxed = true).also { repository ->
            coEvery { repository.getStartConfig() } returns ApiResult.Success(
                StartConfigResultModel(
                    userXieYiUrl = "https://example.com/user",
                    yinSiXieYiUrl = "https://example.com/privacy",
                )
            )
        }

    private fun createViewModel(
        repository: LoginRepository,
        sessionRepository: UserSessionRepository = mockk(relaxed = true),
        preferences: LoginPreferencesManager = defaultPreferences(),
    ): LoginViewModel {
        return LoginViewModel(
            loginRepository = repository,
            userSessionRepository = sessionRepository,
            loginPreferencesManager = preferences,
            textResolver = ResourceTextResolver(ApplicationProvider.getApplicationContext()),
        )
    }

    private fun defaultPreferences(): LoginPreferencesManager =
        mockk<LoginPreferencesManager>().also { preferences ->
            every { preferences.getLastLoginPhoneNumber() } returns ""
            every { preferences.saveLastLoginPhoneNumber(any()) } returns Unit
        }
}
