package com.ytone.longcare.presentation.validation.nfc

import android.view.KeyEvent
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class R65CHidInputViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val parser = ExternalRfidTagParser()

    @Test
    fun `valid uid completes immediately when scanner sends enter`() {
        val viewModel = createViewModel()

        viewModel.onFieldFocusChanged(true)
        "A1B2C3D4".forEach { character ->
            viewModel.onCapturedKey(
                R65CHidCapturedKeyEvent(
                    keyCode = KeyEvent.KEYCODE_UNKNOWN,
                    unicodeChar = character.code,
                ),
            )
        }
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = KeyEvent.KEYCODE_ENTER,
                unicodeChar = '\n'.code,
            ),
        )

        assertEquals(
            R65CHidCaptureState.LastCaptureSucceeded,
            viewModel.panelState.value.captureState,
        )
        assertEquals("A1B2C3D4", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("A1B2C3D4\n", viewModel.panelState.value.lastRawInput)
    }

    @Test
    fun `scanner without enter completes after idle timeout`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onFieldFocusChanged(true)
            "0123456789ABCD".forEach { character ->
                viewModel.onCapturedKey(
                    R65CHidCapturedKeyEvent(
                        keyCode = KeyEvent.KEYCODE_UNKNOWN,
                        unicodeChar = character.code,
                    ),
                )
            }
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals(
                R65CHidCaptureState.LastCaptureSucceeded,
                viewModel.panelState.value.captureState,
            )
            assertEquals("0123456789ABCD", viewModel.panelState.value.lastNormalizedUid)
        }

    @Test
    fun `invalid scanner payload keeps raw value and reports failure`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onFieldFocusChanged(true)
            viewModel.onCapturedKey(
                R65CHidCapturedKeyEvent(
                    keyCode = KeyEvent.KEYCODE_UNKNOWN,
                    unicodeChar = 'Z'.code,
                ),
            )
            advanceTimeBy(400)
            advanceUntilIdle()

            assertTrue(
                viewModel.panelState.value.captureState is R65CHidCaptureState.LastCaptureFailed,
            )
            assertEquals("Z", viewModel.panelState.value.lastRawInput)
            assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
        }

    @Test
    fun `losing focus discards partial scan before the next card`() {
        val viewModel = createViewModel()

        viewModel.onFieldFocusChanged(true)
        sendText(viewModel, "A1B2")
        viewModel.onFieldFocusChanged(false)
        viewModel.onFieldFocusChanged(true)
        sendText(viewModel, "11223344")
        sendEnter(viewModel)

        assertEquals("11223344", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("11223344\n", viewModel.panelState.value.lastRawInput)
    }

    @Test
    fun `manual refocus discards partial scan before the next card`() {
        val viewModel = createViewModel()

        viewModel.onFieldFocusChanged(true)
        sendText(viewModel, "A1B2")
        viewModel.requestRefocus()
        sendText(viewModel, "55667788")
        sendEnter(viewModel)

        assertEquals("55667788", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("55667788\n", viewModel.panelState.value.lastRawInput)
    }

    private fun sendText(
        viewModel: R65CHidInputViewModel,
        value: String,
    ) {
        value.forEach { character ->
            viewModel.onCapturedKey(
                R65CHidCapturedKeyEvent(
                    keyCode = KeyEvent.KEYCODE_UNKNOWN,
                    unicodeChar = character.code,
                ),
            )
        }
    }

    private fun sendEnter(viewModel: R65CHidInputViewModel) {
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = KeyEvent.KEYCODE_ENTER,
                unicodeChar = '\n'.code,
            ),
        )
    }

    private fun createViewModel() =
        R65CHidInputViewModel(
            parser = parser,
            nowProvider = { "12:34:56" },
            completionDelayMillis = 400L,
        )
}
