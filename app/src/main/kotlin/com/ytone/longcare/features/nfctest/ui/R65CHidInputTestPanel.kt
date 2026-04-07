package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState

@Composable
internal fun R65CHidInputTestPanel(
    state: R65CHidPanelState,
    onInputChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onRequestRefocus: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.focusRequestToken) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "R65C HID 键盘口测试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = captureStateLabel(state.captureState),
                modifier = Modifier.testTag("r65c_status_label"),
            )

            OutlinedTextField(
                value = state.liveInputBuffer,
                onValueChange = onInputChanged,
                label = { Text("刷卡输入框") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag("r65c_input_field"),
            )

            Text("实时输入:")
            Text(
                text = state.liveInputBuffer,
                modifier = Modifier.testTag("r65c_live_input_value"),
            )

            Text("最近原始输入:")
            Text(
                text = state.lastRawInputDisplay,
                modifier = Modifier.testTag("r65c_last_raw_value"),
            )

            Text("最近标准化UID:")
            Text(
                text = state.lastNormalizedUidDisplay,
                modifier = Modifier.testTag("r65c_last_uid_value"),
            )

            Text("完成时间:")
            Text(
                text = state.lastCompletedAtDisplay,
                modifier = Modifier.testTag("r65c_last_completed_at"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRequestRefocus,
                    modifier = Modifier.testTag("r65c_refocus_button"),
                ) {
                    Text("重新聚焦")
                }

                Button(
                    onClick = onClearResult,
                    modifier = Modifier.testTag("r65c_clear_button"),
                ) {
                    Text("清空结果")
                }
            }
        }
    }
}

internal fun captureStateLabel(captureState: R65CHidCaptureState): String = when (captureState) {
    R65CHidCaptureState.WaitingForFocus -> "等待聚焦"
    R65CHidCaptureState.ReadyForScan -> "等待刷卡"
    R65CHidCaptureState.ReceivingInput -> "正在输入"
    R65CHidCaptureState.LastCaptureSucceeded -> "捕获成功"
    is R65CHidCaptureState.LastCaptureFailed -> "捕获失败: ${captureState.reason}"
}
