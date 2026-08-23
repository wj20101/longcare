package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.ytone.longcare.R
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
    private val filter: R65cBusinessFallbackFilter,
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
                    if (!granted) {
                        publishReaderError(context.getString(R.string.rfid_permission_denied))
                    }
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
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        publishConnectionState(isReaderReady())
    }

    override fun stop(activity: Activity) {
        if (receiverRegistered) {
            context.unregisterReceiver(usbReceiver)
            receiverRegistered = false
        }
    }

    override fun submitHidCandidate(rawPayload: String) {
        publishCandidate(rawPayload)
    }

    override fun isReaderReady(): Boolean = usbManager.deviceList.isNotEmpty()

    private fun publishConnectionState(connected: Boolean) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderConnectionChanged(connected, ScanSource.EXTERNAL_RFID))
        }
    }

    internal fun publishRawPayload(rawPayload: String) {
        publishCandidate(rawPayload)
    }

    internal fun publishReaderError(message: String) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderError(message, ScanSource.EXTERNAL_RFID))
        }
    }

    private fun publishCandidate(rawPayload: String) {
        when (val result = filter.consume(rawPayload)) {
            is R65cBusinessFallbackResult.Valid -> {
                scope.launch {
                    appEventBus.send(AppEvent.TagScanned(result.tagId, ScanSource.EXTERNAL_RFID))
                }
            }

            is R65cBusinessFallbackResult.DeviceError -> {
                publishReaderError(context.getString(R.string.rfid_read_failed))
            }

            is R65cBusinessFallbackResult.Invalid,
            is R65cBusinessFallbackResult.DuplicateSuppressed,
            -> Unit
        }
    }
}
