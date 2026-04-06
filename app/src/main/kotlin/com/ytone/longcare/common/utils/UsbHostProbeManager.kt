package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.ytone.longcare.features.nfctest.vm.UsbDeviceSummary
import com.ytone.longcare.features.nfctest.vm.UsbEndpointSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface UsbHostProbeManager {
    fun refresh(): UsbHostProbeResult
    fun requestPermission(activity: Activity): UsbHostProbeResult
    fun attemptRead(activity: Activity): UsbHostProbeResult
}

@Singleton
class DefaultUsbHostProbeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsbHostProbeManager {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun refresh(): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        return UsbHostProbeResult.DeviceFound(
            summary = device.toSummary(),
            hasPermission = usbManager.hasPermission(device),
        )
    }

    override fun requestPermission(activity: Activity): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        return UsbHostProbeResult.DeviceFound(
            summary = device.toSummary(),
            hasPermission = usbManager.hasPermission(device),
        )
    }

    override fun attemptRead(activity: Activity): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        val summary = device.toSummary()
        if (!usbManager.hasPermission(device)) {
            return UsbHostProbeResult.ReadFailure(summary, "USB权限未授予")
        }

        val connection = usbManager.openDevice(device)
            ?: return UsbHostProbeResult.ReadFailure(summary, "无法打开USB设备")

        try {
            val usbInterface = if (device.interfaceCount > 0) device.getInterface(0) else null
                ?: return UsbHostProbeResult.ReadFailure(summary, "未找到可用Interface")
            if (!connection.claimInterface(usbInterface, true)) {
                return UsbHostProbeResult.ReadFailure(summary, "无法声明USB Interface")
            }

            val endpoint = (0 until usbInterface.endpointCount)
                .map { usbInterface.getEndpoint(it) }
                .firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
                ?: return UsbHostProbeResult.ReadFailure(summary, "未找到可读Endpoint")

            val buffer = ByteArray(endpoint.maxPacketSize.coerceAtLeast(64))
            val length = connection.bulkTransfer(endpoint, buffer, buffer.size, 300)
            if (length <= 0) {
                return UsbHostProbeResult.ReadFailure(summary, "未读取到原始数据")
            }

            return UsbHostProbeResult.ReadSuccess(summary, buffer.copyOf(length))
        } finally {
            connection.close()
        }
    }

    private fun UsbDevice.toSummary() = UsbDeviceSummary(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaceCount = interfaceCount,
        endpoints = (0 until interfaceCount).flatMap { index ->
            val usbInterface = getInterface(index)
            (0 until usbInterface.endpointCount).map { endpointIndex ->
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                UsbEndpointSummary(
                    address = endpoint.address,
                    direction = endpoint.direction,
                    type = endpoint.type,
                    maxPacketSize = endpoint.maxPacketSize,
                )
            }
        },
    )
}
