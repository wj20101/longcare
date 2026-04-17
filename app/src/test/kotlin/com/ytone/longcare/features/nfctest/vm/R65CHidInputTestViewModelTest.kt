package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
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
class R65CHidInputTestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val parser = mockk<ExternalRfidTagParser>()
    private val fixedNow = "12:34:56"

    @Test
    fun `focus gain marks panel ready`() {
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)

        assertEquals(R65CHidCaptureState.ReadyForScan, viewModel.panelState.value.captureState)
    }

    @Test
    fun `enter completes immediately and clears live buffer`() = runTest {
        every { parser.normalize("ab12\r") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12\r")

        assertEquals("", viewModel.panelState.value.liveInputBuffer)
        assertEquals("ab12\r", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals(1L, viewModel.panelState.value.focusRequestToken)
    }

    @Test
    fun `captured key sequence completes immediately on enter`() {
        every { parser.normalize("AB12\n") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 30,
                unicodeChar = 'B'.code,
                action = 0,
                displayChar = "B",
                eventTimeMillis = 2L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 8,
                unicodeChar = '1'.code,
                action = 0,
                displayChar = "1",
                eventTimeMillis = 3L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 9,
                unicodeChar = '2'.code,
                action = 0,
                displayChar = "2",
                eventTimeMillis = 4L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 66,
                unicodeChar = '\n'.code,
                action = 0,
                displayChar = "\\n",
                eventTimeMillis = 5L,
            ),
        )

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12\n", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `captured key sequence completes after idle timeout without enter`() = runTest {
        every { parser.normalize("AB12") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 30,
                unicodeChar = 'B'.code,
                action = 0,
                displayChar = "B",
                eventTimeMillis = 2L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 8,
                unicodeChar = '1'.code,
                action = 0,
                displayChar = "1",
                eventTimeMillis = 3L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 9,
                unicodeChar = '2'.code,
                action = 0,
                displayChar = "2",
                eventTimeMillis = 4L,
            ),
        )

        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
    }

    @Test
    fun `idle timeout completes when enter does not arrive`() = runTest {
        every { parser.normalize("ab12") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12")

        assertEquals(R65CHidCaptureState.ReceivingInput, viewModel.panelState.value.captureState)
        advanceTimeBy(399)
        assertEquals("ab12", viewModel.panelState.value.liveInputBuffer)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `normalization failure preserves raw input and marks failure`() = runTest {
        every { parser.normalize("###") } returns null
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("###")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.captureState is R65CHidCaptureState.LastCaptureFailed)
        assertEquals(
            "未解析出卡号",
            (viewModel.panelState.value.captureState as R65CHidCaptureState.LastCaptureFailed).reason,
        )
        assertEquals("###", viewModel.panelState.value.lastRawInput)
        assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
    }

    @Test
    fun `clear result removes latest capture and requests focus again`() {
        every { parser.normalize("ab12\r") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12\r")
        viewModel.clearLastResult()

        assertEquals(null, viewModel.panelState.value.lastRawInput)
        assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
        assertEquals(null, viewModel.panelState.value.lastCompletedAt)
        assertEquals(R65CHidCaptureState.ReadyForScan, viewModel.panelState.value.captureState)
        assertEquals(2L, viewModel.panelState.value.focusRequestToken)
    }

    @Test
    fun `terminator only input completes immediately as failed capture`() {
        every { parser.normalize("\r") } returns null
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("\r")

        assertTrue(viewModel.panelState.value.captureState is R65CHidCaptureState.LastCaptureFailed)
        assertEquals(
            "未解析出卡号",
            (viewModel.panelState.value.captureState as R65CHidCaptureState.LastCaptureFailed).reason,
        )
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
        assertEquals("\r", viewModel.panelState.value.lastRawInput)
        assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
        assertEquals(1L, viewModel.panelState.value.focusRequestToken)
    }

    @Test
    fun `focus loss cancels pending completion`() = runTest {
        every { parser.normalize(any()) } returns "SHOULD_NOT_HAPPEN"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("AB")
        viewModel.onFieldFocusChanged(false)
        advanceTimeBy(401)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.WaitingForFocus, viewModel.panelState.value.captureState)
        assertEquals(null, viewModel.panelState.value.lastRawInput)
        assertEquals("AB", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `new input resets idle completion timer`() = runTest {
        every { parser.normalize("AB") } returns "AB"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("A")
        advanceTimeBy(300)
        viewModel.onInputChanged("AB")
        advanceTimeBy(100)

        assertEquals(R65CHidCaptureState.ReceivingInput, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.liveInputBuffer)
        assertEquals(null, viewModel.panelState.value.lastRawInput)

        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.lastRawInput)
        assertEquals("AB", viewModel.panelState.value.lastNormalizedUid)
    }

    @Test
    fun `empty input sets ready for scan and clears live buffer`() {
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("A")
        viewModel.onInputChanged("")

        assertEquals(R65CHidCaptureState.ReadyForScan, viewModel.panelState.value.captureState)
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `request refocus keeps receiving state when buffer not empty and increments token`() {
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("AB")
        val tokenBefore = viewModel.panelState.value.focusRequestToken

        viewModel.requestRefocus()

        assertEquals(R65CHidCaptureState.ReceivingInput, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.liveInputBuffer)
        assertEquals(tokenBefore + 1, viewModel.panelState.value.focusRequestToken)
    }
}
