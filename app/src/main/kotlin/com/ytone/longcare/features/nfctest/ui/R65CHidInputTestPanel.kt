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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState

@Composable
internal fun R65CHidInputTestPanel(
    state: R65CHidPanelState,
    onRequestRefocus: () -> Unit,
    onClearResult: () -> Unit,
    onCopyResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            Text("当前会话输入:")
            Text(
                text = state.liveInputBuffer.ifBlank { "-" },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onRequestRefocus,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("r65c_refocus_button"),
                ) {
                    Text("重新聚焦")
                }

                Button(
                    onClick = onClearResult,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("r65c_clear_button"),
                ) {
                    Text("清空结果")
                }

                Button(
                    onClick = onCopyResult,
                    enabled = !state.lastNormalizedUid.isNullOrBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("r65c_copy_button"),
                ) {
                    Text("复制结果")
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
