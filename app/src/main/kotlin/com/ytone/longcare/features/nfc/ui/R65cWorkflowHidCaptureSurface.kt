package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfc.vm.ReaderUiState

@Composable
internal fun R65cWorkflowHidCaptureSurface(
    readerUiState: ReaderUiState,
    onKeyCaptured: (R65cWorkflowHidCapturedKeyEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(readerUiState) {
        if (readerUiState == ReaderUiState.Ready || readerUiState == ReaderUiState.Reading) {
            keyboardController?.hide()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                toR65cWorkflowHidCapturedKeyEventIfRelevant(keyEvent.nativeKeyEvent)
                    ?.let(onKeyCaptured)
                false
            },
    )
}
