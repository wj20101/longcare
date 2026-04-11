package com.ytone.longcare.features.nfc.ui

import android.view.KeyEvent

internal data class R65cWorkflowHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)

internal fun toR65cWorkflowHidCapturedKeyEventIfRelevant(
    keyEvent: KeyEvent,
): R65cWorkflowHidCapturedKeyEvent? {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return null
    if (keyEvent.keyCode in IGNORED_NON_SCAN_KEYS) return null

    val displayChar = when {
        keyEvent.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent.unicodeChar == '\n'.code -> "\\n"
        keyEvent.unicodeChar == 0 -> ""
        else -> keyEvent.unicodeChar.toChar().toString()
    }

    return R65cWorkflowHidCapturedKeyEvent(
        keyCode = keyEvent.keyCode,
        unicodeChar = keyEvent.unicodeChar,
        displayChar = displayChar,
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
