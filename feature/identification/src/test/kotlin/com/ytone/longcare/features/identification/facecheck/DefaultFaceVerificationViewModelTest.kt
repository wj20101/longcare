package com.ytone.longcare.features.identification.facecheck

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.features.identification.domain.CheckFaceGateway
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.features.identification.domain.CheckFaceUseCase
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultFaceVerificationViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `camera bitmap is recycled after successful verification`() = runTest(mainDispatcher) {
        val gateway = ResultGateway(CheckFaceRemoteResult.Success)
        val viewModel = DefaultFaceVerificationViewModel(
            imageEncoder = FaceImageEncoder(mainDispatcher),
            checkFaceUseCase = CheckFaceUseCase(gateway),
        )
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        viewModel.verifyFace(OrderKey(orderId = 123L), bitmap)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(DefaultFaceVerificationUiState.Success)
        assertThat(gateway.callCount).isEqualTo(1)
        val metrics = viewModel.photoMetrics.first()
        assertThat(metrics.widthPx).isEqualTo(64)
        assertThat(metrics.heightPx).isEqualTo(64)
        assertThat(metrics.byteCount).isGreaterThan(0)
        assertThat(bitmap.isRecycled).isTrue()
    }

    @Test
    fun `missing registered face is terminal and cannot be mistaken for retryable mismatch`() =
        runTest(mainDispatcher) {
            val viewModel = createViewModel(CheckFaceRemoteResult.MissingRegisteredFace)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

            viewModel.verifyFace(OrderKey(orderId = 123L), bitmap)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value)
                .isInstanceOf(DefaultFaceVerificationUiState.TerminalError::class.java)
            assertThat(bitmap.isRecycled).isTrue()
        }

    @Test
    fun `session invalidation waits for global logout navigation without retry`() =
        runTest(mainDispatcher) {
            val viewModel = createViewModel(CheckFaceRemoteResult.SessionInvalidated)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

            viewModel.verifyFace(OrderKey(orderId = 123L), bitmap)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value)
                .isEqualTo(DefaultFaceVerificationUiState.SessionInvalidated)
            assertThat(bitmap.isRecycled).isTrue()
        }

    private fun createViewModel(result: CheckFaceRemoteResult) =
        DefaultFaceVerificationViewModel(
            imageEncoder = FaceImageEncoder(mainDispatcher),
            checkFaceUseCase = CheckFaceUseCase(ResultGateway(result)),
        )

    private class ResultGateway(
        private val result: CheckFaceRemoteResult,
    ) : CheckFaceGateway {
        var callCount = 0

        override suspend fun checkFace(
            orderId: Int,
            faceImageBase64: String,
        ): CheckFaceRemoteResult {
            callCount += 1
            return result
        }
    }
}
