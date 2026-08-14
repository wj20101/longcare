package com.ytone.longcare.presentation.validation.nfc

internal sealed interface R65CHidCaptureState {
    data object WaitingForFocus : R65CHidCaptureState
    data object ReadyForScan : R65CHidCaptureState
    data object ReceivingInput : R65CHidCaptureState
    data object LastCaptureSucceeded : R65CHidCaptureState
    data object LastCaptureFailed : R65CHidCaptureState
}

internal data class R65CHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
)

internal data class R65CHidPanelState(
    val captureState: R65CHidCaptureState = R65CHidCaptureState.WaitingForFocus,
    val liveInputBuffer: String = "",
    val lastRawInput: String? = null,
    val lastNormalizedUid: String? = null,
    val lastCompletedAt: String? = null,
    val focusRequestToken: Long = 0L,
)
