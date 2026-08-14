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
        val gateway = SuccessGateway()
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

    private class SuccessGateway : CheckFaceGateway {
        var callCount = 0

        override suspend fun checkFace(
            orderId: Int,
            faceImageBase64: String,
        ): CheckFaceRemoteResult {
            callCount += 1
            return CheckFaceRemoteResult.Success
        }
    }
}
