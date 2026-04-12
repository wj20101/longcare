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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateKind
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateValue
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState

@Composable
internal fun R65CHidRawValidationPanel(
    state: R65CHidRawValidationState,
    onTextFieldValueChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onRequestRefocus: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

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
                text = "R65C 原始 HID 输出验证",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = rawValidationStateLabel(state.captureState),
                modifier = Modifier.testTag("r65c_raw_status"),
            )

            OutlinedTextField(
                value = state.textFieldValue,
                onValueChange = onTextFieldValueChanged,
                label = { Text("原始验证输入框") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag("r65c_raw_input_field"),
            )

            Text("文本框结果")
            Text(
                text = state.lastSessionTextFieldValueDisplay,
                modifier = Modifier.testTag("r65c_raw_text_value"),
            )

            Text("按键拼装结果")
            Text(
                text = state.lastSessionAssembledCharsDisplay,
                modifier = Modifier.testTag("r65c_raw_assembled_value"),
            )

            Text("结束原因")
            Text(
                text = state.lastCompletedReasonDisplay,
                modifier = Modifier.testTag("r65c_completed_reason"),
            )

            Text("完成时间")
            Text(
                text = state.lastCompletedAtDisplay,
                modifier = Modifier.testTag("r65c_completed_at"),
            )

            CandidateValuesSection(candidateValues = state.candidateValues)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        onStartListening()
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier.testTag("r65c_raw_start_button"),
                    enabled = !state.isListening,
                ) {
                    Text("开始监听")
                }

                Button(
                    onClick = onStopListening,
                    modifier = Modifier.testTag("r65c_raw_stop_button"),
                    enabled = state.isListening,
                ) {
                    Text("停止监听")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        onRequestRefocus()
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier.testTag("r65c_raw_refocus_button"),
                ) {
                    Text("重新聚焦")
                }

                Button(
                    onClick = onClearSession,
                    modifier = Modifier.testTag("r65c_raw_clear_button"),
                ) {
                    Text("清空会话")
                }
            }
        }
    }
}

@Composable
private fun CandidateValuesSection(candidateValues: List<R65CHidCandidateValue>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("候选值列表")

        if (candidateValues.isEmpty()) {
            Text("-")
            return
        }

        candidateValues.forEachIndexed { index, candidate ->
            Text(
                text = candidateKindLabel(candidate.kind),
                modifier = Modifier.testTag("r65c_candidate_${index}_kind"),
            )
            Text(
                text = candidate.value,
                modifier = Modifier.testTag("r65c_candidate_${index}_value"),
            )
            Text(candidate.note)
        }
    }
}

internal fun rawValidationStateLabel(captureState: R65CHidRawCaptureState): String = when (captureState) {
    R65CHidRawCaptureState.Idle -> "未开始监听"
    R65CHidRawCaptureState.Armed -> "等待刷卡"
    R65CHidRawCaptureState.Capturing -> "正在接收按键"
    R65CHidRawCaptureState.Completed -> "采集完成"
    is R65CHidRawCaptureState.CaptureError -> "采集异常: ${captureState.message}"
}

private fun candidateKindLabel(kind: R65CHidCandidateKind): String = when (kind) {
    R65CHidCandidateKind.RawText -> "文本框原始值"
    R65CHidCandidateKind.RawAssembled -> "按键拼装值"
    R65CHidCandidateKind.HexFiltered -> "十六进制过滤"
    R65CHidCandidateKind.DecimalToHex -> "十进制转十六进制"
    R65CHidCandidateKind.ReversedFourByteHex -> "四字节反转十六进制"
    R65CHidCandidateKind.Classification -> "分类结果"
}
