package com.ytone.longcare.features.face.ui

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.features.face.viewmodel.ManualFaceCaptureViewModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.Executor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManualFaceCaptureProcessorTest {
    private val executor = Executor { it.run() }

    private fun viewModel() = ManualFaceCaptureViewModel(
        mockk(relaxed = true), ResourceTextResolver(ApplicationProvider.getApplicationContext()),
    ).also { it.setCameraPermissionGranted(true) }

    @Test
    fun `unavailable capture reports error instead of remaining loading`() {
        val model = viewModel()
        takeManualFacePhoto(null, executor, model)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.currentState.value is ManualFaceCaptureState.Error)
    }

    @Test
    fun `duplicate click does not dispatch a second capture`() {
        val capture = mockk<ImageCapture>()
        every { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) } just Runs
        val model = viewModel()
        repeat(2) { takeManualFacePhoto(capture, executor, model) }
        verify(exactly = 1) { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) }
        assertTrue(model.uiState.value.isLoading)
    }

    @Test
    fun `synchronous dispatch failure clears loading and allows retry`() {
        val capture = mockk<ImageCapture>()
        every { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) } throws
            IllegalStateException("capture dispatch rejected")
        val model = viewModel()
        takeManualFacePhoto(capture, executor, model)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.currentState.value is ManualFaceCaptureState.Error)
        every { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) } just Runs
        takeManualFacePhoto(capture, executor, model)
        assertTrue(model.uiState.value.isLoading)
    }

    @Test
    fun `asynchronous failure clears loading and allows retry`() {
        val capture = mockk<ImageCapture>()
        val callback = slot<ImageCapture.OnImageCapturedCallback>()
        every { capture.takePicture(any<Executor>(), capture(callback)) } just Runs
        val model = viewModel()
        takeManualFacePhoto(capture, executor, model)
        callback.captured.onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "unbound", null))
        assertFalse(model.uiState.value.isLoading)
        takeManualFacePhoto(capture, executor, model)
        verify(exactly = 2) { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) }
    }

    @Test
    fun `revoked camera permission blocks capture dispatch`() {
        val capture = mockk<ImageCapture>()
        val model = viewModel()
        model.setCameraPermissionGranted(false)
        takeManualFacePhoto(capture, executor, model)
        verify(exactly = 0) { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) }
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun `permission recovery allows a fresh capture after rejection`() {
        val capture = mockk<ImageCapture>()
        every { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) } just Runs
        val model = viewModel()
        model.setCameraPermissionGranted(false)
        takeManualFacePhoto(capture, executor, model)
        model.setCameraPermissionGranted(true)
        takeManualFacePhoto(capture, executor, model)
        verify(exactly = 1) { capture.takePicture(any<Executor>(), any<ImageCapture.OnImageCapturedCallback>()) }
        assertTrue(model.uiState.value.isLoading)
    }
}
