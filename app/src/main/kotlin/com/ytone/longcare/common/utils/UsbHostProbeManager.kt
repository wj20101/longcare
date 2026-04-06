package com.ytone.longcare.common.utils

import android.app.Activity

interface UsbHostProbeManager {
    fun refresh(): UsbHostProbeResult
    fun requestPermission(activity: Activity): UsbHostProbeResult
    fun attemptRead(activity: Activity): UsbHostProbeResult
}
