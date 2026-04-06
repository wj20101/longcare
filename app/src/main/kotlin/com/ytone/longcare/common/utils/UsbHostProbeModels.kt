package com.ytone.longcare.common.utils

data class UsbEndpointSummary(
    val address: Int,
    val direction: Int,
    val type: Int,
    val maxPacketSize: Int,
)

data class UsbDeviceSummary(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val endpoints: List<UsbEndpointSummary>,
)

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
