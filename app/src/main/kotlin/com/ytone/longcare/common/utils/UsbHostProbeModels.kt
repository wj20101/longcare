package com.ytone.longcare.common.utils

import com.ytone.longcare.features.nfctest.vm.UsbDeviceSummary

sealed class UsbHostProbeResult {
    data object NoDevice : UsbHostProbeResult()
    data class DeviceFound(
        val summary: UsbDeviceSummary,
        val hasPermission: Boolean,
    ) : UsbHostProbeResult()
    data class ReadSuccess(
        val summary: UsbDeviceSummary,
        val payload: ByteArray,
    ) : UsbHostProbeResult()
    data class ReadFailure(
        val summary: UsbDeviceSummary?,
        val message: String,
    ) : UsbHostProbeResult()
}
