package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbExternalRfidReaderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appEventBus: AppEventBus,
    private val parser: ExternalRfidTagParser,
) : ExternalRfidReaderManager {

    private val permissionAction = "${context.packageName}.USB_PERMISSION_RFID"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private var receiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> publishConnectionState(true)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> publishConnectionState(false)
                permissionAction -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) publishReaderError("未授予读卡器访问权限")
                }
            }
        }
    }

    override fun start(activity: Activity) {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(permissionAction)
            }
            context.registerReceiver(usbReceiver, filter)
            receiverRegistered = true
        }
        publishConnectionState(usbManager.deviceList.isNotEmpty())
    }

    override fun stop(activity: Activity) {
        if (receiverRegistered) {
            context.unregisterReceiver(usbReceiver)
            receiverRegistered = false
        }
    }

    private fun publishConnectionState(connected: Boolean) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderConnectionChanged(connected, ScanSource.EXTERNAL_RFID))
        }
    }

    internal fun publishRawPayload(rawPayload: String) {
        val tagId = parser.normalize(rawPayload) ?: return
        scope.launch {
            appEventBus.send(AppEvent.TagScanned(tagId, ScanSource.EXTERNAL_RFID))
        }
    }

    internal fun publishReaderError(message: String) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderError(message, ScanSource.EXTERNAL_RFID))
        }
    }
}
