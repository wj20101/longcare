package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class R65CHidRawValidationContractsTest {

    @Test
    fun `default raw validation state starts empty and waiting for focus`() {
        val state = R65CHidRawValidationState()

        assertEquals(R65CHidRawCaptureState.WaitingForFocus, state.captureState)
        assertEquals("", state.textFieldValue)
        assertEquals("", state.currentSessionAssembledChars)
        assertEquals("-", state.lastSessionTextFieldValueDisplay)
        assertEquals("-", state.lastSessionAssembledCharsDisplay)
        assertEquals("-", state.lastCompletedReasonDisplay)
        assertEquals("-", state.lastCompletedAtDisplay)
        assertEquals(0, state.lastSessionEvents.size)
        assertEquals(0, state.candidateValues.size)
        assertEquals(0L, state.focusRequestToken)
    }

    @Test
    fun `completed state exposes displays and candidate summary`() {
        val state = R65CHidRawValidationState(
            captureState = R65CHidRawCaptureState.Completed,
            lastSessionTextFieldValue = "901948不EA8想0想",
            lastSessionAssembledChars = "901948EA80",
            lastCompletedReason = R65CHidCompletionReason.EnterKey,
            candidateValues = listOf(
                R65CHidCandidateValue(
                    kind = R65CHidCandidateKind.HexFiltered,
                    value = "901948EA80",
                    note = "looks like 10 hex",
                ),
            ),
            lastCompletedAt = "21:52:05",
        )

        assertEquals("901948不EA8想0想", state.lastSessionTextFieldValueDisplay)
        assertEquals("901948EA80", state.lastSessionAssembledCharsDisplay)
        assertEquals("Enter结束", state.lastCompletedReasonDisplay)
        assertEquals("21:52:05", state.lastCompletedAtDisplay)
        assertEquals("901948EA80", state.candidateValues.single().value)
    }
}
