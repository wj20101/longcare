package com.ytone.longcare.presentation.validation.nfc

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
internal fun R65CHidInputCapture(
    enabled: Boolean,
    focusRequestToken: Long,
    onFocusChanged: (Boolean) -> Unit,
    onKeyCaptured: (R65CHidCapturedKeyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(enabled, focusRequestToken) {
        if (enabled) {
            keyboardController?.hide()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier =
            modifier
                .size(1.dp)
                .clearAndSetSemantics { }
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .focusable(enabled)
                .onPreviewKeyEvent { composeEvent ->
                    if (!enabled) return@onPreviewKeyEvent false

                    val captured = composeEvent.nativeKeyEvent.toR65CCapturedKeyOrNull()
                    if (captured != null) {
                        onKeyCaptured(captured)
                        true
                    } else {
                        false
                    }
                },
    )
}

internal fun KeyEvent.toR65CCapturedKeyOrNull(): R65CHidCapturedKeyEvent? {
    if (action != KeyEvent.ACTION_DOWN || keyCode in IGNORED_SYSTEM_KEYS) return null

    val isTerminator =
        keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            unicodeChar == '\n'.code ||
            unicodeChar == '\r'.code
    if (unicodeChar == 0 && !isTerminator) return null

    return R65CHidCapturedKeyEvent(
        keyCode = keyCode,
        unicodeChar = unicodeChar,
    )
}

private val IGNORED_SYSTEM_KEYS =
    setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
    )
