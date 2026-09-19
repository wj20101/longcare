package com.ytone.longcare.assistant

import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.LoginPreferencesManager
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.login.vm.*
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.StartConfigResultModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantLoginContractTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = mockk<LoginRepository>()
    private val session = mockk<UserSessionRepository>(relaxed = true)
    private val preferences = mockk<LoginPreferencesManager>(relaxed = true)

    private fun model(): LoginViewModel {
        coEvery { repository.getStartConfig() } returns ApiResult.Success(StartConfigResultModel())
        return LoginViewModel(repository, session, preferences,
            ResourceTextResolver(ApplicationProvider.getApplicationContext()))
    }

    @Test fun `consent and invalid input prevent sms and login requests`() = runTest {
        val vm = model()
        vm.sendSmsCode("13800138000")
        vm.login("13800138000", "123456")
        assertNotNull(vm.feedback.value)
        vm.onPrivacyAgreementConfirmed()
        listOf("", "123", "12800138000", "138001380001").forEach {
            vm.sendSmsCode(it); vm.login(it, "123456")
        }
        vm.login("13800138000", " ")
        runCurrent()
        coVerify(exactly = 0) { repository.sendSmsCode(any()); repository.login(any(), any()) }
        coVerify(exactly = 0) { session.login(any()) }
    }

    @Test fun `sms success counts down and permits retry after sixty seconds`() = runTest {
        coEvery { repository.sendSmsCode(any()) } returns ApiResult.Success(Unit)
        val vm = model()
        vm.onPrivacyAgreementConfirmed()
        vm.sendSmsCode("13800138000")
        runCurrent()
        assertEquals(SendSmsCodeUiState.Success, vm.sendSmsCodeState.value)
        assertEquals(60, vm.countdownSeconds.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(59, vm.countdownSeconds.value)
        advanceTimeBy(59_000); runCurrent()
        assertEquals(0, vm.countdownSeconds.value)
        vm.sendSmsCode("13800138000"); runCurrent()
        assertEquals(60, vm.countdownSeconds.value)
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.sendSmsCode("13800138000") }
    }

    @Test fun `sms and login failures remain retryable without saving a session`() = runTest {
        coEvery { repository.sendSmsCode(any()) } returns ApiResult.Failure(400, "sms rejected")
        coEvery { repository.login(any(), any()) } returns ApiResult.Failure(400, "invalid code")
        val vm = model()
        vm.onPrivacyAgreementConfirmed()
        vm.sendSmsCode("13800138000"); vm.login("13800138000", "123456")
        runCurrent()
        assertTrue(vm.sendSmsCodeState.value is SendSmsCodeUiState.Error)
        assertTrue(vm.loginState.value is LoginUiState.Error)
        assertEquals(0, vm.countdownSeconds.value)
        coVerify(exactly = 0) { session.login(any()) }
        verify(exactly = 0) { preferences.saveLastLoginPhoneNumber(any()) }
    }

    @Test fun `login success saves only the injected assistant session and phone`() = runTest {
        coEvery { repository.login(any(), any()) } returns ApiResult.Success(
            LoginResultModel(userId = 42, userName = "Test", token = "test-only"))
        val vm = model()
        vm.onPrivacyAgreementConfirmed()
        vm.login("13800138000", "123456"); runCurrent()
        assertEquals(42, (vm.loginState.value as LoginUiState.Success).user.userId)
        coVerify(exactly = 1) { session.login(match { it.userId == 42 && it.token == "test-only" }) }
        verify(exactly = 1) { preferences.saveLastLoginPhoneNumber("13800138000") }
    }

    @Test fun `order accepts only supported positive integer bounds without truncation`() {
        listOf("", "0", "-1", "abc", "1.5", "2147483648", "999999999999999999999999").forEach {
            assertNull(it, validAssistantOrderId(it))
        }
        assertEquals(1L, validAssistantOrderId("1"))
        assertEquals(2147483647L, validAssistantOrderId("2147483647"))
    }
}
