package com.ytone.longcare.features.nfctest.vm

sealed interface R65CHidRawCaptureState {
    data object Idle : R65CHidRawCaptureState
    data object Armed : R65CHidRawCaptureState
    data object Capturing : R65CHidRawCaptureState
    data object Completed : R65CHidRawCaptureState
    data class CaptureError(val message: String) : R65CHidRawCaptureState
}

enum class R65CHidCompletionReason {
    EnterKey,
    IdleTimeout,
}

enum class R65CHidCandidateKind {
    RawText,
    RawAssembled,
    HexFiltered,
    DecimalToHex,
    ReversedFourByteHex,
    Classification,
}

data class R65CHidCandidateValue(
    val kind: R65CHidCandidateKind,
    val value: String,
    val note: String,
)

data class R65CHidRawValidationState(
    val captureState: R65CHidRawCaptureState = R65CHidRawCaptureState.Idle,
    val isListening: Boolean = false,
    val textFieldValue: String = "",
    val currentSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val currentSessionAssembledChars: String = "",
    val lastSessionTextFieldValue: String? = null,
    val lastSessionAssembledChars: String? = null,
    val lastSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val lastCompletedReason: R65CHidCompletionReason? = null,
    val candidateValues: List<R65CHidCandidateValue> = emptyList(),
    val lastCompletedAt: String? = null,
) {
    val lastSessionTextFieldValueDisplay: String
        get() = lastSessionTextFieldValue ?: "-"

    val lastSessionAssembledCharsDisplay: String
        get() = lastSessionAssembledChars ?: "-"

    val lastCompletedReasonDisplay: String
        get() = when (lastCompletedReason) {
            R65CHidCompletionReason.EnterKey -> "Enter结束"
            R65CHidCompletionReason.IdleTimeout -> "超时结束"
            null -> "-"
        }

    val lastCompletedAtDisplay: String
        get() = lastCompletedAt ?: "-"
}
