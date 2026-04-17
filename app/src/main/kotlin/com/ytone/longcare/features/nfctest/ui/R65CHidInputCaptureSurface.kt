package com.ytone.longcare.features.nfctest.ui

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
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent

@Composable
internal fun R65CHidInputCaptureSurface(
    enabled: Boolean,
    focusRequestToken: Long,
    onFocusChanged: (Boolean) -> Unit,
    onKeyCaptured: (R65CHidCapturedKeyEvent) -> Unit,
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
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (!enabled) {
                    return@onPreviewKeyEvent false
                }

                toR65CHidCapturedKeyEventIfRelevant(keyEvent.nativeKeyEvent)?.let(onKeyCaptured)
                false
            },
    )
}
