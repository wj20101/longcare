package com.ytone.longcare.features.shared.vm

import android.graphics.Bitmap
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.shared.FaceVerificationPhotoProcessor
import com.ytone.longcare.features.shared.ProcessedFacePhoto
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceVerificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val systemConfigManager = mockk<SystemConfigManager>(relaxed = true)
    private val photoProcessor = mockk<FaceVerificationPhotoProcessor>()

    private fun createViewModel(): FaceVerificationViewModel {
        return FaceVerificationViewModel(
            systemConfigManager = systemConfigManager,
            photoProcessor = photoProcessor,
        )
    }

    @Test
    fun `captured photo processing exposes success and can be consumed`() = runTest {
        val processedPhoto =
            ProcessedFacePhoto(
                bitmap = mockk<Bitmap>(),
                base64 = "encoded-photo",
            )
        coEvery { photoProcessor.process("/tmp/face.jpg") } returns processedPhoto
        val viewModel = createViewModel()

        viewModel.processCapturedPhoto("/tmp/face.jpg")
        advanceUntilIdle()

        assertEquals(
            FaceVerificationViewModel.PhotoProcessingState.Success(processedPhoto),
            viewModel.photoProcessingState.value,
        )
        viewModel.clearPhotoProcessingState()
        assertEquals(
            FaceVerificationViewModel.PhotoProcessingState.Idle,
            viewModel.photoProcessingState.value,
        )
    }

    @Test
    fun `captured photo processing exposes formal error message`() = runTest {
        coEvery { photoProcessor.process(any()) } throws IllegalStateException("图片文件不存在")
        val viewModel = createViewModel()

        viewModel.processCapturedPhoto("/missing.jpg")
        advanceUntilIdle()

        assertEquals(
            FaceVerificationViewModel.PhotoProcessingState.Error("图片文件不存在"),
            viewModel.photoProcessingState.value,
        )
    }

    @Test
    fun `startFaceVerificationWithAutoSign should emit error when config missing`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns null
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            name = "test",
            idNo = "123",
            orderNo = "order",
            userId = "user"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state but was $state", state is FaceVerificationViewModel.FaceVerifyUiState.Error)
        assertEquals(
            "人脸验证配置不可用，请重新登录后重试",
            (state as FaceVerificationViewModel.FaceVerifyUiState.Error).message
        )
        assertEquals(null, viewModel.sdkLaunchRequest.value)
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose resolved sdk launch request`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            name = "test",
            idNo = "123",
            orderNo = "order",
            userId = "user"
        )
        advanceUntilIdle()

        val launchRequest = requireNotNull(viewModel.sdkLaunchRequest.value)
        assertEquals(FaceVerificationConfig("appId", "secret", "licence"), launchRequest.config)
        assertEquals("test", launchRequest.request.name)
        assertEquals("123", launchRequest.request.idNo)
        assertEquals(null, launchRequest.request.sourcePhotoStr)
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose callback failure as ui error`() = runTest {
        val sdkError = FaceVerifyError(code = "E001", description = "verify failed")
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()
        val launchId = requireNotNull(viewModel.sdkLaunchRequest.value).id
        viewModel.onFaceSdkEvent(launchId, FaceSdkEvent.VerifyFailed(sdkError))

        val state = viewModel.uiState.value
        assertTrue("Expected Error state but was $state", state is FaceVerificationViewModel.FaceVerifyUiState.Error)
        val errorState = state as FaceVerificationViewModel.FaceVerifyUiState.Error
        assertEquals(sdkError, errorState.error)
        assertEquals("人脸验证失败：verify failed", errorState.message)
    }

    @Test
    fun `startFaceVerificationWithAutoSign should expose init success as verifying state`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returns FaceVerificationConfig(
            appId = "appId",
            secret = "secret",
            licence = "licence"
        )
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()
        val launchId = requireNotNull(viewModel.sdkLaunchRequest.value).id
        viewModel.onFaceSdkEvent(launchId, FaceSdkEvent.InitSuccess)

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
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign(
            orderNo = "order",
            userId = "user",
            sourcePhotoStr = "base64"
        )
        advanceUntilIdle()
        val launchId = requireNotNull(viewModel.sdkLaunchRequest.value).id
        viewModel.onFaceSdkEvent(launchId, FaceSdkEvent.VerifySuccess(result))

        assertEquals(
            FaceVerificationViewModel.FaceVerifyUiState.Success(result),
            viewModel.uiState.value
        )
    }

    @Test
    fun `new preparation clears stale launch when refreshed config is missing`() = runTest {
        coEvery { systemConfigManager.getFaceVerificationConfig() } returnsMany
            listOf(
                FaceVerificationConfig("appId", "secret", "licence"),
                null,
            )
        val viewModel = createViewModel()

        viewModel.startFaceVerificationWithAutoSign("name", "id", "order", "user")
        advanceUntilIdle()
        val staleLaunch = requireNotNull(viewModel.sdkLaunchRequest.value)

        viewModel.startFaceVerificationWithAutoSign("name", "id", "order", "user")
        advanceUntilIdle()

        assertNull(viewModel.sdkLaunchRequest.value)
        viewModel.onFaceSdkEvent(staleLaunch.id, FaceSdkEvent.InitSuccess)
        assertTrue(viewModel.uiState.value is FaceVerificationViewModel.FaceVerifyUiState.Error)
    }
}
