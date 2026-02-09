package com.ytone.longcare.features.shared.vm

import android.content.Context
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.faceauth.FaceVerifyCallback
import com.ytone.longcare.domain.faceauth.FaceVerifier
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val faceVerifier = mockk<FaceVerifier>(relaxed = true)
    private val systemConfigManager = mockk<SystemConfigManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun createViewModel(): FaceVerificationViewModel {
        return FaceVerificationViewModel(
            faceVerifier = faceVerifier,
            systemConfigManager = systemConfigManager
        )
    }

    @Test
    fun `startFaceVerificationWithAutoSign should emit error when config missing`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns null
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            context = context,
            name = "test",
            idNo = "123",
            orderNo = "order",
            userId = "user"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is FaceVerificationViewModel.FaceVerifyUiState.Error)
        assertEquals("人脸配置不可用", (state as FaceVerificationViewModel.FaceVerifyUiState.Error).message)
        coVerify(exactly = 0) { faceVerifier.startFaceVerification(any(), any(), any(), any()) }
    }

    @Test
    fun `startFaceVerificationWithAutoSign should pass resolved config to verifier`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        coEvery { faceVerifier.startFaceVerification(any(), any(), any(), any()) } coAnswers {}
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            context = context,
            name = "test",
            idNo = "123",
            orderNo = "order",
            userId = "user"
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            faceVerifier.startFaceVerification(
                context = context,
                config = FaceVerificationConfig("appId", "secret", "licence"),
                request = match { it.name == "test" && it.idNo == "123" && it.sourcePhotoStr == null },
                callback = any()
            )
        }
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose callback failure as ui error`() = runTest {
        val sdkError = FaceVerifyError(code = "E001", description = "verify failed")
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        coEvery { faceVerifier.startFaceVerification(any(), any(), any(), any()) } coAnswers {
            val callback = arg<FaceVerifyCallback>(3)
            callback.onVerifyFailed(sdkError)
        }
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            context = context,
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is FaceVerificationViewModel.FaceVerifyUiState.Error)
        val errorState = state as FaceVerificationViewModel.FaceVerifyUiState.Error
        assertEquals(sdkError, errorState.error)
        assertEquals("人脸验证失败: verify failed", errorState.message)
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose init success as verifying state`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        coEvery { faceVerifier.startFaceVerification(any(), any(), any(), any()) } coAnswers {
            val callback = arg<FaceVerifyCallback>(3)
            callback.onInitSuccess()
        }
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            context = context,
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()

        assertEquals(FaceVerificationViewModel.FaceVerifyUiState.Verifying, viewModel.uiState.value)
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose callback success as ui success`() = runTest {
        val result = FaceVerifyResult(isSuccess = true, error = null)
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        coEvery { faceVerifier.startFaceVerification(any(), any(), any(), any()) } coAnswers {
            val callback = arg<FaceVerifyCallback>(3)
            callback.onVerifySuccess(result)
        }
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            context = context,
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()

        assertEquals(
            FaceVerificationViewModel.FaceVerifyUiState.Success(result),
            viewModel.uiState.value
        )
    }
}
