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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

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

    private fun createViewModel(repository: LoginRepository): LoginViewModel {
        val preferences = mockk<LoginPreferencesManager>()
        every { preferences.getLastLoginPhoneNumber() } returns ""
        every { preferences.saveLastLoginPhoneNumber(any()) } returns Unit

        return LoginViewModel(
            loginRepository = repository,
            userSessionRepository = mockk<UserSessionRepository>(relaxed = true),
            loginPreferencesManager = preferences
        )
    }
}
