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
    fun `start listening arms validation and stop listening returns idle`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        assertEquals(R65CHidRawCaptureState.Armed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)

        viewModel.stopListening()
        assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
        assertEquals(false, viewModel.panelState.value.isListening)
    }

    @Test
    fun `host captured keys assemble one session`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 29, unicodeChar = 'A'.code, action = 0, displayChar = "A", eventTimeMillis = 1L),
        )
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 30, unicodeChar = 'B'.code, action = 0, displayChar = "B", eventTimeMillis = 2L),
        )

        assertEquals(R65CHidRawCaptureState.Capturing, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.currentSessionAssembledChars)
        assertEquals(2, viewModel.panelState.value.currentSessionEvents.size)
    }

    @Test
    fun `enter completes session immediately`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("0426FAFA051F91")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 66, unicodeChar = '\n'.code, action = 0, displayChar = "\\n", eventTimeMillis = 3L),
        )

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)
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

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("4210697732")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 8, unicodeChar = '4'.code, action = 0, displayChar = "4", eventTimeMillis = 1L),
        )

        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)
        assertEquals(R65CHidCompletionReason.IdleTimeout, viewModel.panelState.value.lastCompletedReason)
        assertTrue(viewModel.panelState.value.candidateValues.isNotEmpty())
    }

    @Test
    fun `focus text field no longer grants authority over raw capture state`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onTextFieldValueChanged("AB")
        viewModel.requestRefocus()
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 29, unicodeChar = 'A'.code, action = 0, displayChar = "A", eventTimeMillis = 1L),
        )

        assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
        assertEquals(false, viewModel.panelState.value.isListening)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
    }

    @Test
    fun `clearLastSession returns to armed when still listening`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 29, unicodeChar = 'A'.code, action = 0, displayChar = "A", eventTimeMillis = 1L),
        )
        viewModel.clearLastSession()

        assertEquals(R65CHidRawCaptureState.Armed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)
        assertTrue(viewModel.panelState.value.candidateValues.isEmpty())
    }

    @Test
    fun `clearLastSession clears live text and last-session state while listening`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.clearLastSession()

        assertEquals(R65CHidRawCaptureState.Armed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)
        assertEquals("", viewModel.panelState.value.textFieldValue)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
        assertEquals(null, viewModel.panelState.value.lastSessionTextFieldValue)
        assertEquals(null, viewModel.panelState.value.lastSessionAssembledChars)
    }

    @Test
    fun `clearLastSession while idle clears live text and stays idle`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onTextFieldValueChanged("AB")
        viewModel.clearLastSession()

        assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
        assertEquals(false, viewModel.panelState.value.isListening)
        assertEquals("", viewModel.panelState.value.textFieldValue)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
    }
}
