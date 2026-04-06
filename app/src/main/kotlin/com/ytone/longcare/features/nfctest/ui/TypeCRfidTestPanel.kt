package com.ytone.longcare.features.nfctest.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.TypeCRfidPanelState

@Composable
internal fun TypeCRfidTestPanel(
    state: TypeCRfidPanelState,
    activity: Activity?,
    onRefresh: () -> Unit,
    onRequestPermission: (Activity) -> Unit,
    onAttemptRead: (Activity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Type-C USB Host 测试", style = MaterialTheme.typography.titleMedium)
            Text("状态: ${state.probeState}")
            Text("设备: ${state.deviceSummary?.deviceName ?: "未检测到USB设备"}")
            Text("VID/PID: ${state.deviceSummary?.vendorId ?: "-"} / ${state.deviceSummary?.productId ?: "-"}")
            Text("接口数: ${state.deviceSummary?.interfaceCount ?: 0}")
            Text("原始文本: ${state.rawPayloadText ?: "-"}")
            Text("原始HEX: ${state.rawPayloadHex.ifBlank { "-" }}")
            Text("解析结果: ${state.parsedTagDisplay}")
            Text("最近更新时间: ${state.lastUpdatedAt ?: "-"}")

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("刷新设备")
            }
            Button(
                onClick = { activity?.let(onRequestPermission) },
                modifier = Modifier.fillMaxWidth(),
                enabled = activity != null,
            ) {
                Text("申请权限")
            }
            Button(
                onClick = { activity?.let(onAttemptRead) },
                modifier = Modifier.fillMaxWidth(),
                enabled = activity != null,
            ) {
                Text("开始尝试读取")
            }
        }
    }
}
