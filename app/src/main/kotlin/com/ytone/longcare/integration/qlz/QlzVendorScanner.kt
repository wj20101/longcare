package com.ytone.longcare.integration.qlz

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.evenmed.sdk.call.ScanDeviceIml
import com.evenmed.sdk.call.h
import java.util.ArrayList

/** Owns one vendor scan and its callbacks for the lifetime of the detection screen. */
internal class QlzVendorScanner(
    context: Context,
    private val onStarted: () -> Unit,
    private val onStopped: () -> Unit,
    private val onDevices: (ArrayList<BluetoothDevice>?) -> Unit,
) : ScanDeviceIml(context) {
    private var closed = false
    // QLZ 1.3.0.5 only weakly owns ScanDeviceIml's internal listener (h.d).
    // Keep it alive across GC and retries; validate these AAR members when upgrading the SDK.
    private var retainedCallback: h.g? = checkNotNull(bluetoothLeScan.d.get())

    override fun startScan(timeoutMillis: Long) {
        check(!closed)
        bluetoothLeScan.a(checkNotNull(retainedCallback))
        super.startScan(timeoutMillis)
    }

    override fun scanStart() {
        if (!closed) onStarted()
    }

    override fun scanStop() {
        if (!closed) onStopped()
    }

    override fun scanChange(devices: ArrayList<BluetoothDevice>?) {
        if (!closed) onDevices(devices)
    }

    fun close() {
        if (closed) return
        closed = true
        super.stopScan()
        bluetoothLeScan.d.clear()
        retainedCallback = null
    }
}
