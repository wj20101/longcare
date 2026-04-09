package com.ytone.longcare.features.nfctest.vm

sealed interface R65CHidRawCaptureState {
    data object WaitingForFocus : R65CHidRawCaptureState
    data object ReadyForScan : R65CHidRawCaptureState
    data object ReceivingKeys : R65CHidRawCaptureState
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

data class R65CHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val action: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)

data class R65CHidCandidateValue(
    val kind: R65CHidCandidateKind,
    val value: String,
    val note: String,
)

data class R65CHidRawValidationState(
    val captureState: R65CHidRawCaptureState = R65CHidRawCaptureState.WaitingForFocus,
    val textFieldValue: String = "",
    val currentSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val currentSessionAssembledChars: String = "",
    val lastSessionTextFieldValue: String? = null,
    val lastSessionAssembledChars: String? = null,
    val lastSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val lastCompletedReason: R65CHidCompletionReason? = null,
    val candidateValues: List<R65CHidCandidateValue> = emptyList(),
    val lastCompletedAt: String? = null,
    val focusRequestToken: Long = 0L,
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
