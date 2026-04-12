package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState

fun toR65CHidCapturedKeyEventIfRelevant(
    isListening: Boolean,
    currentState: R65CHidRawCaptureState,
    keyEvent: KeyEvent,
): R65CHidCapturedKeyEvent? {
    if (!isListening) {
        return null
    }

    val isCaptureState =
        currentState is R65CHidRawCaptureState.Armed ||
            currentState is R65CHidRawCaptureState.Capturing
    if (!isCaptureState) {
        return null
    }

    if (keyEvent.action != KeyEvent.ACTION_DOWN) {
        return null
    }

    if (keyEvent.keyCode in IGNORED_NON_SCAN_KEYS) {
        return null
    }

    return R65CHidCapturedKeyEvent(
        keyCode = keyEvent.keyCode,
        unicodeChar = keyEvent.unicodeChar,
        action = keyEvent.action,
        displayChar = keyEvent.toDisplayChar(),
        eventTimeMillis = keyEvent.eventTime,
    )
}

private val IGNORED_NON_SCAN_KEYS = setOf(
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_APP_SWITCH,
)

private fun KeyEvent.toDisplayChar(): String {
    if (keyCode == KeyEvent.KEYCODE_ENTER || unicodeChar == '\n'.code) {
        return "\\n"
    }

    if (unicodeChar == 0) {
        return ""
    }

    return unicodeChar.toChar().toString()
}
