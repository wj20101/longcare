package com.ytone.longcare.features.nfctest.vm

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
class R65CHidRawValidationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val fixedNow = "21:52:05"

    @Test
    fun `key events are assembled into one session`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 29, unicodeChar = 'A'.code, action = 0, displayChar = "A", eventTimeMillis = 1L),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 30, unicodeChar = 'B'.code, action = 0, displayChar = "B", eventTimeMillis = 2L),
        )

        assertEquals(R65CHidRawCaptureState.ReceivingKeys, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.currentSessionAssembledChars)
        assertEquals(2, viewModel.panelState.value.currentSessionEvents.size)
    }

    @Test
    fun `enter completes session immediately`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("0426FAFA051F91")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 66, unicodeChar = '\n'.code, action = 0, displayChar = "\\n", eventTimeMillis = 3L),
        )

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals("0426FAFA051F91", viewModel.panelState.value.lastSessionTextFieldValue)
        assertEquals(R65CHidCompletionReason.EnterKey, viewModel.panelState.value.lastCompletedReason)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
    }

    @Test
    fun `idle timeout completes session`() = runTest {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("4210697732")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 8, unicodeChar = '4'.code, action = 0, displayChar = "4", eventTimeMillis = 1L),
        )

        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals(R65CHidCompletionReason.IdleTimeout, viewModel.panelState.value.lastCompletedReason)
        assertTrue(viewModel.panelState.value.candidateValues.isNotEmpty())
    }
}
