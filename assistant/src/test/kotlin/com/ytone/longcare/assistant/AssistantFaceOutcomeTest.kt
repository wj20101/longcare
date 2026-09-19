package com.ytone.longcare.assistant

import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantFaceOutcomeTest {
    @Test fun `Tencent success publishes result before returning home`() {
        val events = mutableListOf<String>()
        completeAssistantFaceVerification(
            message = "success",
            report = { events += it },
            returnHome = { events += "home" },
        )
        assertEquals(listOf("success", "home"), events)
    }

    @Test fun `exit distinguishes cancellation error and success`() {
        assertEquals(AssistantFaceOutcome.CANCELLED, faceOutcomeOnExit(DefaultFaceVerificationUiState.Capturing()))
        assertEquals(AssistantFaceOutcome.CANCELLED, faceOutcomeOnExit(DefaultFaceVerificationUiState.Verifying))
        assertEquals(AssistantFaceOutcome.FAILED, faceOutcomeOnExit(DefaultFaceVerificationUiState.RetryableError()))
        assertEquals(AssistantFaceOutcome.FAILED, faceOutcomeOnExit(DefaultFaceVerificationUiState.SessionInvalidated))
        assertEquals(AssistantFaceOutcome.SUCCESS, faceOutcomeOnExit(DefaultFaceVerificationUiState.Success))
    }
}
