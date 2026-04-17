package com.ytone.longcare.features.nfctest.vm

sealed interface R65CHidCaptureState {
    data object WaitingForFocus : R65CHidCaptureState
    data object ReadyForScan : R65CHidCaptureState
    data object ReceivingInput : R65CHidCaptureState
    data object LastCaptureSucceeded : R65CHidCaptureState
    data class LastCaptureFailed(val reason: String) : R65CHidCaptureState
}

data class R65CHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val action: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)

data class R65CHidPanelState(
    val captureState: R65CHidCaptureState = R65CHidCaptureState.WaitingForFocus,
    val liveInputBuffer: String = "",
    val lastRawInput: String? = null,
    val lastNormalizedUid: String? = null,
    val lastCompletedAt: String? = null,
    val focusRequestToken: Long = 0L,
) {
    val lastRawInputDisplay: String
        get() = lastRawInput ?: "-"

    val lastNormalizedUidDisplay: String
        get() = lastNormalizedUid?.takeIf(String::isNotBlank) ?: "未解析出卡号"

    val lastCompletedAtDisplay: String
        get() = lastCompletedAt ?: "-"
}
