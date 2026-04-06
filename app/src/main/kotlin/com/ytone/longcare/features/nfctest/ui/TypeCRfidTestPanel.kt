package com.ytone.longcare.features.nfctest.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.common.utils.UsbDeviceSummary
import com.ytone.longcare.common.utils.UsbEndpointSummary
import com.ytone.longcare.features.nfctest.vm.TypeCRfidPanelState
import com.ytone.longcare.features.nfctest.vm.UsbProbeUiState

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
            Text(
                text = "Type-C USB Host 测试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            ProbeStatusChip(probeState = state.probeState)
            Text("设备: ${state.deviceSummary?.deviceName ?: "未检测到USB设备"}")
            Text("权限: ${formatPermissionState(state.probeState)}")
            Text("VID/PID: ${formatVidPid(state.deviceSummary)}")
            Text("Class/Subclass/Protocol: ${formatDeviceClassInfo(state.deviceSummary)}")
            Text("接口数: ${formatInterfaceCount(state.deviceSummary)}")
            Text("Endpoint摘要: ${formatEndpoints(state.deviceSummary?.endpoints)}")
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

@Composable
private fun ProbeStatusChip(probeState: UsbProbeUiState) {
    val (label, icon, color) = when (probeState) {
        UsbProbeUiState.Idle -> Triple("待检测", Icons.Default.HourglassBottom, Color(0xFF8E8E93))
        UsbProbeUiState.NoDevice -> Triple("未检测到设备", Icons.Default.Error, Color(0xFF8E8E93))
        UsbProbeUiState.DeviceDetected -> Triple("已检测到设备", Icons.Default.CheckCircle, Color(0xFF3A86FF))
        UsbProbeUiState.PermissionDenied -> Triple("权限被拒绝", Icons.Default.Error, Color(0xFFD32F2F))
        UsbProbeUiState.Ready -> Triple("就绪", Icons.Default.CheckCircle, Color(0xFF2E7D32))
        UsbProbeUiState.Reading -> Triple("读取中", Icons.Default.HourglassBottom, Color(0xFFEF6C00))
        is UsbProbeUiState.ReadFailed -> Triple("读取失败", Icons.Default.Error, Color(0xFFD32F2F))
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (probeState is UsbProbeUiState.ReadFailed) {
                    "$label: ${probeState.message}"
                } else {
                    label
                },
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun formatProbeState(probeState: UsbProbeUiState): String = when (probeState) {
    UsbProbeUiState.Idle -> "待检测"
    UsbProbeUiState.NoDevice -> "未检测到设备"
    UsbProbeUiState.DeviceDetected -> "已检测到设备"
    UsbProbeUiState.PermissionDenied -> "权限被拒绝"
    UsbProbeUiState.Ready -> "就绪"
    UsbProbeUiState.Reading -> "读取中"
    is UsbProbeUiState.ReadFailed -> "读取失败: ${probeState.message}"
}

internal fun formatVidPid(deviceSummary: UsbDeviceSummary?): String = if (deviceSummary == null) {
    "- / -"
} else {
    "0x%04X / 0x%04X".format(
        deviceSummary.vendorId and 0xFFFF,
        deviceSummary.productId and 0xFFFF,
    )
}

internal fun formatInterfaceCount(deviceSummary: UsbDeviceSummary?): String =
    deviceSummary?.interfaceCount?.toString() ?: "-"

internal fun formatPermissionState(probeState: UsbProbeUiState): String = when (probeState) {
    UsbProbeUiState.PermissionDenied -> "未授予"
    UsbProbeUiState.DeviceDetected -> "待申请"
    UsbProbeUiState.Ready, UsbProbeUiState.Reading, is UsbProbeUiState.ReadFailed -> "已授予"
    UsbProbeUiState.Idle, UsbProbeUiState.NoDevice -> "-"
}

internal fun formatDeviceClassInfo(deviceSummary: UsbDeviceSummary?): String = if (deviceSummary == null) {
    "- / - / -"
} else {
    "${deviceSummary.deviceClass} / ${deviceSummary.deviceSubclass} / ${deviceSummary.deviceProtocol}"
}

internal fun formatEndpoints(endpoints: List<UsbEndpointSummary>?): String = if (endpoints.isNullOrEmpty()) {
    "-"
} else {
    endpoints.joinToString(separator = "; ") { endpoint ->
        "addr=0x%02X dir=%d type=%d size=%d".format(
            endpoint.address and 0xFF,
            endpoint.direction,
            endpoint.type,
            endpoint.maxPacketSize,
        )
    }
}
