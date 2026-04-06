package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.common.utils.UsbDeviceSummary

sealed class UsbProbeUiState {
    data object Idle : UsbProbeUiState()
    data object NoDevice : UsbProbeUiState()
    data object DeviceDetected : UsbProbeUiState()
    data object PermissionDenied : UsbProbeUiState()
    data object Ready : UsbProbeUiState()
    data object Reading : UsbProbeUiState()
    data class ReadFailed(val message: String) : UsbProbeUiState()
}

data class TypeCRfidPanelState(
    val probeState: UsbProbeUiState = UsbProbeUiState.Idle,
    val deviceSummary: UsbDeviceSummary? = null,
    val rawPayload: ByteArray? = null,
    val rawPayloadText: String? = null,
    val parsedTagId: String? = null,
    val lastUpdatedAt: String? = null,
) {
    val rawPayloadHex: String = rawPayload
        ?.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        .orEmpty()

    val parsedTagDisplay: String = parsedTagId ?: "未解析出卡号"
}
