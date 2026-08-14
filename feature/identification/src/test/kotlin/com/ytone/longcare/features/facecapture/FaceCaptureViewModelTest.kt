package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FaceCaptureViewModelTest {
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
    fun `three second preparation countdown blocks premature capture`() = runTest(mainDispatcher) {
        val viewModel = FaceCaptureViewModel()
        val premature = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

        viewModel.startPreparationCountdown()
        viewModel.onFaceCaptured(premature, quality = 0.95f)

        assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.PREPARING)
        assertThat(viewModel.uiState.value.countdownSeconds).isEqualTo(3)
        assertThat(viewModel.uiState.value.isDetectionEnabled).isFalse()
        assertThat(viewModel.uiState.value.captureReady).isFalse()
        assertThat(premature.isRecycled).isTrue()

        advanceTimeBy(1_000L)
        runCurrent()
        assertThat(viewModel.uiState.value.countdownSeconds).isEqualTo(2)

        advanceTimeBy(2_000L)
        runCurrent()
        assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.SCANNING)
        assertThat(viewModel.uiState.value.countdownSeconds).isEqualTo(0)
        assertThat(viewModel.uiState.value.isDetectionEnabled).isTrue()
    }

    @Test
    fun `stable progress enters confirming and loss of face resets it`() = runTest(mainDispatcher) {
        val viewModel = scanningViewModel()

        viewModel.updateFaceDetectionState(
            FaceDetectionSnapshot(
                detected = true,
                quality = 0.91f,
                confirmationProgress = 0.45f,
            ),
        )

        assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.CONFIRMING)
        assertThat(viewModel.uiState.value.confirmationProgress).isEqualTo(0.45f)

        viewModel.updateFaceDetectionState(
            FaceDetectionSnapshot(
                detected = false,
                quality = 0f,
                confirmationProgress = 0f,
            ),
        )

        assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.SCANNING)
        assertThat(viewModel.uiState.value.confirmationProgress).isEqualTo(0f)
    }

    @Test
    fun `first stable camera face is accepted and later frames are discarded`() =
        runTest(mainDispatcher) {
            val viewModel = scanningViewModel()
            val first = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val later = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

            viewModel.onFaceCaptured(first, quality = 0.91f)
            viewModel.onFaceCaptured(later, quality = 0.96f)

            assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.CAPTURED)
            assertThat(viewModel.uiState.value.captureReady).isTrue()
            assertThat(viewModel.uiState.value.confirmationProgress).isEqualTo(1f)
            assertThat(later.isRecycled).isTrue()

            val transferred = viewModel.takeCapturedFace()

            assertThat(transferred).isSameInstanceAs(first)
            assertThat(first.isRecycled).isFalse()
            assertThat(viewModel.uiState.value.captureReady).isFalse()
            first.recycle()
        }

    @Test
    fun `reset recycles an undelivered face and requires a new countdown`() =
        runTest(mainDispatcher) {
            val viewModel = scanningViewModel()
            val first = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val next = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

            viewModel.onFaceCaptured(first, quality = 0.9f)
            viewModel.resetCapture()
            viewModel.onFaceCaptured(next, quality = 0.92f)

            assertThat(first.isRecycled).isTrue()
            assertThat(next.isRecycled).isTrue()
            assertThat(viewModel.uiState.value.phase).isEqualTo(FaceCapturePhase.STARTING)
            assertThat(viewModel.uiState.value.captureReady).isFalse()
        }

    private suspend fun TestScope.scanningViewModel(): FaceCaptureViewModel {
        val viewModel = FaceCaptureViewModel()
        viewModel.startPreparationCountdown()
        advanceTimeBy(3_000L)
        runCurrent()
        return viewModel
    }
}
