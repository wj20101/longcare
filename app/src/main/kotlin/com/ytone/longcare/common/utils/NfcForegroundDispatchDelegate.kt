package com.ytone.longcare.common.utils

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

internal object NfcForegroundDispatchDelegate {

    fun isResumed(activity: Activity): Boolean {
        return (activity as? LifecycleOwner)
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED)
            ?: true
    }

    fun disableSafely(activity: Activity, requireResumed: Boolean) {
        if (requireResumed && !isResumed(activity)) return
        try {
            NfcUtils.disableForegroundDispatch(activity)
        } catch (exception: IllegalStateException) {
            logE("Activity 已离开前台，NFC 前台分发无需重复关闭", throwable = exception)
        }
    }
}
