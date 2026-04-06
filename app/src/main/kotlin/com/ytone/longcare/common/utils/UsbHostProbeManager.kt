package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class UsbHostDeviceEvent {
    data object Attached : UsbHostDeviceEvent()
    data object Detached : UsbHostDeviceEvent()
}

interface UsbHostProbeManager {
    fun refresh(): UsbHostProbeResult
    fun requestPermission(activity: Activity): UsbHostProbeResult
    fun attemptRead(activity: Activity): UsbHostProbeResult
    fun observeDeviceChanges(): Flow<UsbHostDeviceEvent>
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
        // Placeholder behavior for Task 2: report current permission state only.
        // Real PendingIntent + BroadcastReceiver permission flow will be added later.
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

        var usbInterfaceClaimed = false
        var usbInterface: android.hardware.usb.UsbInterface? = null
        try {
            if (device.interfaceCount <= 0) {
                return UsbHostProbeResult.ReadFailure(summary, "未找到可用Interface")
            }
            usbInterface = device.getInterface(0)
            if (!connection.claimInterface(usbInterface, false)) {
                return UsbHostProbeResult.ReadFailure(summary, "无法声明USB Interface")
            }
            usbInterfaceClaimed = true

            val endpoint = (0 until usbInterface.endpointCount)
                .map { usbInterface.getEndpoint(it) }
                .firstOrNull {
                    it.direction == UsbConstants.USB_DIR_IN &&
                        it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
                ?: return UsbHostProbeResult.ReadFailure(summary, "未找到可读Bulk Endpoint")

            val buffer = ByteArray(endpoint.maxPacketSize.coerceAtLeast(64))
            val length = connection.bulkTransfer(endpoint, buffer, buffer.size, 300)
            if (length <= 0) {
                return UsbHostProbeResult.ReadFailure(summary, "未读取到原始数据")
            }

            return UsbHostProbeResult.ReadSuccess(summary, buffer.copyOf(length))
        } finally {
            if (usbInterfaceClaimed && usbInterface != null) {
                connection.releaseInterface(usbInterface)
            }
            connection.close()
        }
    }

    override fun observeDeviceChanges(): Flow<UsbHostDeviceEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> trySend(UsbHostDeviceEvent.Attached)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> trySend(UsbHostDeviceEvent.Detached)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose {
            context.unregisterReceiver(receiver)
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
