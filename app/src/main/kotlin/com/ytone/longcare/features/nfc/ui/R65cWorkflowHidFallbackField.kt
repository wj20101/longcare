package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfc.vm.ReaderUiState
import kotlinx.coroutines.delay

@Composable
internal fun R65cWorkflowHidFallbackField(
    readerUiState: ReaderUiState,
    onInputChanged: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var liveBuffer by remember { mutableStateOf("") }

    LaunchedEffect(readerUiState) {
        if (readerUiState == ReaderUiState.Ready || readerUiState == ReaderUiState.Reading) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(liveBuffer) {
        if (liveBuffer.isBlank()) return@LaunchedEffect

        if (liveBuffer.contains('\n') || liveBuffer.contains('\r')) {
            val settled = liveBuffer.replace("\n", "").replace("\r", "")
            liveBuffer = ""
            if (settled.isNotBlank()) {
                onInputChanged(settled)
            }
            return@LaunchedEffect
        }

        delay(400L)
        if (liveBuffer.isBlank()) return@LaunchedEffect

        val settled = liveBuffer
        liveBuffer = ""
        if (settled.isNotBlank()) {
            onInputChanged(settled)
        }
    }

    OutlinedTextField(
        value = liveBuffer,
        onValueChange = { liveBuffer = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(0.dp)
            .focusRequester(focusRequester)
            .testTag("nfc_workflow_r65c_hid_input"),
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}
