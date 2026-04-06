package com.ytone.longcare.common.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.app.PendingIntentCompat
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
    data class PermissionChanged(val granted: Boolean) : UsbHostDeviceEvent()
}

interface UsbHostProbeManager {
    fun refresh(): UsbHostProbeResult
    fun requestPermission(activity: Activity): UsbHostProbeResult
    fun attemptRead(activity: Activity): UsbHostProbeResult
    fun observeDeviceChanges(): Flow<UsbHostDeviceEvent>
}

internal data class UsbReadTarget(
    val usbInterface: UsbInterface,
    val endpoint: UsbEndpoint,
)

@Singleton
class DefaultUsbHostProbeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsbHostProbeManager {

    private val permissionAction = "${context.packageName}.USB_HOST_PROBE_PERMISSION"
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
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = Intent(permissionAction).setPackage(context.packageName)
            val pendingIntent = PendingIntentCompat.getBroadcast(
                context,
                1001,
                permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )

            if (pendingIntent != null) {
                usbManager.requestPermission(device, pendingIntent)
            }
        }

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

        val readTarget = device.findReadableTarget()
            ?: return UsbHostProbeResult.ReadFailure(summary, "未找到可读Endpoint")

        var usbInterfaceClaimed = false
        try {
            if (!connection.claimInterface(readTarget.usbInterface, false)) {
                return UsbHostProbeResult.ReadFailure(summary, "无法声明USB Interface")
            }
            usbInterfaceClaimed = true

            val buffer = ByteArray(readTarget.endpoint.maxPacketSize.coerceAtLeast(64))
            val length = connection.bulkTransfer(readTarget.endpoint, buffer, buffer.size, 300)
            if (length <= 0) {
                return UsbHostProbeResult.ReadFailure(summary, "未读取到原始数据")
            }

            return UsbHostProbeResult.ReadSuccess(summary, buffer.copyOf(length))
        } finally {
            if (usbInterfaceClaimed) {
                connection.releaseInterface(readTarget.usbInterface)
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
                    permissionAction -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        trySend(UsbHostDeviceEvent.PermissionChanged(granted))
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
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

internal fun UsbDevice.findReadableTarget(): UsbReadTarget? {
    var interruptFallback: UsbReadTarget? = null

    for (interfaceIndex in 0 until interfaceCount) {
        val usbInterface = getInterface(interfaceIndex)
        for (endpointIndex in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(endpointIndex)
            if (endpoint.direction != UsbConstants.USB_DIR_IN) continue

            when (endpoint.type) {
                UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                    return UsbReadTarget(usbInterface, endpoint)
                }

                UsbConstants.USB_ENDPOINT_XFER_INT -> {
                    if (interruptFallback == null) {
                        interruptFallback = UsbReadTarget(usbInterface, endpoint)
                    }
                }
            }
        }
    }

    return interruptFallback
}
